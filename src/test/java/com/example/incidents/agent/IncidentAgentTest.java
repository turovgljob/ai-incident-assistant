package com.example.incidents.agent;

import com.example.incidents.llm.FakeLlmClient;
import com.example.incidents.llm.LlmMessage;
import com.example.incidents.llm.LlmRequest;
import com.example.incidents.model.AnalysisMeta;
import com.example.incidents.model.AnalysisResult;
import org.junit.jupiter.api.Test;

import static com.example.incidents.agent.TestFixtures.PAYGATE_INCIDENT;
import static com.example.incidents.agent.TestFixtures.RUSSIAN_RESPONSE;
import static com.example.incidents.agent.TestFixtures.VALID_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentAgentTest {

    private final FakeLlmClient llm = new FakeLlmClient();
    private final IncidentAgent agent = TestFixtures.agent(llm);

    @Test
    void acceptsValidResponseOnFirstAttempt() {
        llm.reply(VALID_RESPONSE);

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.analysis().category()).isEqualTo("External payment provider issue");
        assertThat(result.analysis().severity()).isEqualTo("high");
        assertThat(result.meta().source()).isEqualTo(AnalysisMeta.Source.LLM);
        assertThat(result.meta().attempts()).isEqualTo(1);
        assertThat(result.meta().errors()).isEmpty();
        assertThat(result.meta().similarIncidents()).first().isEqualTo("INC-101");
    }

    @Test
    void sendsPlatformContextAndRetrievedIncidentsToTheModel() {
        llm.reply(VALID_RESPONSE);

        agent.analyze(PAYGATE_INCIDENT);

        LlmRequest request = llm.requests().getFirst();
        assertThat(request.system())
                .contains("reporting-service: generating reports")
                .contains("DB degradation caused by reporting")
                .contains("\"next_steps\"");
        assertThat(request.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo(LlmMessage.Role.USER);
            assertThat(message.content())
                    .contains("timeouts when calling PayGate")
                    .contains("Mentioned services: payment-service")
                    .contains("Times: 12:05 UTC")
                    .contains("[INC-101]");
        });
    }

    @Test
    void retriesInvalidJsonWithValidatorFeedback() {
        String truncated = "{\"category\": \"External payment provider issue\", \"summary\": \"PayGate";
        llm.reply(truncated, VALID_RESPONSE);

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.meta().source()).isEqualTo(AnalysisMeta.Source.LLM);
        assertThat(result.meta().attempts()).isEqualTo(2);
        assertThat(result.meta().errors()).singleElement().asString().contains("not valid JSON");
        LlmRequest retry = llm.requests().get(1);
        assertThat(retry.messages()).hasSize(3);
        assertThat(retry.messages().get(1)).isEqualTo(LlmMessage.assistant(truncated));
        assertThat(retry.messages().get(2).content())
                .contains("rejected by the validator")
                .contains("not valid JSON");
    }

    @Test
    void extractsJsonFromMarkdownWithoutRetry() {
        llm.reply("Here is the analysis:\n```json\n" + VALID_RESPONSE + "```\nLet me know if you need more.");

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.meta().attempts()).isEqualTo(1);
        assertThat(result.analysis().hypotheses()).hasSize(1);
    }

    @Test
    void regeneratesResponseInUnexpectedLanguage() {
        llm.reply(RUSSIAN_RESPONSE, VALID_RESPONSE);

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.meta().attempts()).isEqualTo(2);
        assertThat(result.meta().errors()).singleElement().asString().contains("must be written in English");
        assertThat(result.analysis().summary()).startsWith("PayGate is not responding");
    }

    @Test
    void autoCorrectsRecoverableDeviationsWithoutRetry() {
        llm.reply("""
                {
                  "analysis": {
                    "category": "external payment provider issue",
                    "summary": "PayGate times out and card payments fail for most customers.",
                    "severity": "High",
                    "confidence": 0.8,
                    "hypotheses": [
                      {"title": "PayGate outage", "reasoning": "Only PayGate calls time out.", "nextSteps": ["Check PayGate status page.", "Compare PayGate latency with other providers.", "Check PayGate error codes.", "Call PayGate support."]},
                      {"title": "Network issue", "reasoning": "Egress to PayGate may be broken.", "next_steps": ["Check egress firewall logs.", "Run traceroute to PayGate."]},
                      {"title": "Expired credentials", "reasoning": "Rejected credentials can look like failures.", "next_steps": ["Check credential expiry dates.", "Check auth errors from PayGate."]},
                      {"title": "Client thread pool exhaustion", "reasoning": "Slow calls can exhaust the pool.", "next_steps": ["Check thread pool metrics.", "Check connection pool metrics."]}
                    ]
                  }
                }
                """);

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.meta().attempts()).isEqualTo(1);
        assertThat(result.analysis().category()).isEqualTo("External payment provider issue");
        assertThat(result.analysis().severity()).isEqualTo("high");
        assertThat(result.analysis().hypotheses()).hasSize(3);
        assertThat(result.analysis().hypotheses().getFirst().nextSteps()).hasSize(3);
        assertThat(result.meta().repairs()).hasSize(7);
    }

    @Test
    void regeneratesResponseWithUnknownCategory() {
        llm.reply(VALID_RESPONSE.replace("External payment provider issue", "Payment gateway outage"), VALID_RESPONSE);

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(result.meta().attempts()).isEqualTo(2);
        assertThat(result.meta().errors()).singleElement().asString().contains("not one of the allowed categories");
    }

    @Test
    void fallsBackToRulesAfterMaxAttempts() {
        llm.reply("I am not sure.", "{\"category\": 42}", "[]");

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(llm.requests()).hasSize(3);
        assertThat(llm.requests().getLast().messages()).hasSize(5);
        assertThat(result.meta().source()).isEqualTo(AnalysisMeta.Source.FALLBACK);
        assertThat(result.meta().attempts()).isEqualTo(3);
        assertThat(result.meta().errors()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.analysis().category()).isEqualTo("External payment provider issue");
        assertThat(result.analysis().severity()).isEqualTo("high");
        assertThat(result.analysis().summary()).startsWith("LLM analysis was unavailable");
    }

    @Test
    void fallsBackImmediatelyWhenTheModelIsUnavailable() {
        llm.fail("Anthropic API call failed: 529 overloaded");

        AnalysisResult result = agent.analyze(PAYGATE_INCIDENT);

        assertThat(llm.requests()).hasSize(1);
        assertThat(result.meta().source()).isEqualTo(AnalysisMeta.Source.FALLBACK);
        assertThat(result.meta().attempts()).isEqualTo(1);
        assertThat(result.meta().errors()).containsExactly("attempt 1: Anthropic API call failed: 529 overloaded");
    }
}
