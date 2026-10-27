package com.example.incidents.agent;

import org.junit.jupiter.api.Test;

import static com.example.incidents.agent.TestFixtures.RUSSIAN_RESPONSE;
import static com.example.incidents.agent.TestFixtures.VALID_RESPONSE;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseValidatorTest {

    private final ResponseValidator validator = TestFixtures.validator();

    @Test
    void acceptsSchemaConformingResponse() {
        ValidationOutcome outcome = validator.validate(VALID_RESPONSE);

        assertThat(outcome.isValid()).isTrue();
        assertThat(outcome.repairs()).isEmpty();
        assertThat(outcome.analysis().orElseThrow().hypotheses().getFirst().nextSteps()).hasSize(2);
    }

    @Test
    void rejectsResponseWithoutJson() {
        ValidationOutcome outcome = validator.validate("Sorry, I cannot help with that.");

        assertThat(outcome.errors()).containsExactly("The response does not contain a JSON object");
    }

    @Test
    void rejectsBrokenJson() {
        ValidationOutcome outcome = validator.validate("{\"category\": \"External payment provider issue\",}");

        assertThat(outcome.errors()).singleElement().asString().startsWith("The response is not valid JSON");
    }

    @Test
    void reportsSchemaViolations() {
        ValidationOutcome outcome = validator.validate("""
                {
                  "category": "External payment provider issue",
                  "summary": "Too short",
                  "severity": "urgent",
                  "hypotheses": [
                    {"title": "PayGate outage", "reasoning": "Only PayGate calls time out.", "next_steps": ["Check status page."]}
                  ]
                }
                """);

        assertThat(outcome.isValid()).isFalse();
        assertThat(outcome.errors()).hasSize(3).anySatisfy(error -> assertThat(error).contains("/severity"))
                .anySatisfy(error -> assertThat(error).contains("/summary"))
                .anySatisfy(error -> assertThat(error).contains("/hypotheses/0/next_steps"));
    }

    @Test
    void reportsMissingFields() {
        ValidationOutcome outcome = validator.validate("{\"category\": \"External payment provider issue\"}");

        assertThat(outcome.errors()).hasSize(3).allSatisfy(error -> assertThat(error).contains("required property"));
    }

    @Test
    void rejectsNonEnglishText() {
        ValidationOutcome outcome = validator.validate(RUSSIAN_RESPONSE);

        assertThat(outcome.errors()).containsExactly(
                "Text fields must be written in English, but the detected language is 'ru'");
    }

    @Test
    void mapsSeveritySynonyms() {
        ValidationOutcome outcome = validator.validate(VALID_RESPONSE.replace("\"high\"", "\"Critical\""));

        assertThat(outcome.analysis().orElseThrow().severity()).isEqualTo("high");
        assertThat(outcome.repairs()).containsExactly("Normalized severity 'Critical' to 'high'");
    }
}
