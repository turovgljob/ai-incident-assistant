package com.example.incidents.agent;

import com.example.incidents.knowledge.KnowledgeBase;
import com.example.incidents.llm.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;

final class TestFixtures {

    static final KnowledgeBase KNOWLEDGE_BASE = new KnowledgeBase();
    static final AnalysisSchema SCHEMA = new AnalysisSchema();
    static final AgentProperties PROPERTIES = new AgentProperties(3, 2);

    static final String PAYGATE_INCIDENT = """
            Customers complain that card payments often fail, and transactions do not go through.
            payment-service logs show many timeouts when calling PayGate, starting from 12:05 UTC.
            Other services look normal.
            """;

    static final String VALID_RESPONSE = """
            {
              "category": "External payment provider issue",
              "summary": "PayGate is not responding in time, so card payments fail for most customers since 12:05 UTC.",
              "severity": "high",
              "hypotheses": [
                {
                  "title": "Degradation or incident on the PayGate side",
                  "reasoning": "Timeouts are observed only when calling PayGate, other services remain stable.",
                  "next_steps": [
                    "Check PayGate status page and recent provider notifications.",
                    "Compare error and latency metrics for PayGate vs other payment providers."
                  ]
                }
              ]
            }
            """;

    static final String RUSSIAN_RESPONSE = """
            {
              "category": "External payment provider issue",
              "summary": "Провайдер PayGate не отвечает вовремя, поэтому платежи по картам не проходят.",
              "severity": "high",
              "hypotheses": [
                {
                  "title": "Деградация на стороне PayGate",
                  "reasoning": "Таймауты наблюдаются только при вызовах PayGate.",
                  "next_steps": [
                    "Проверить страницу статуса PayGate.",
                    "Сравнить метрики ошибок PayGate с другими провайдерами."
                  ]
                }
              ]
            }
            """;

    private TestFixtures() {
    }

    static ResponseValidator validator() {
        return new ResponseValidator(KNOWLEDGE_BASE, SCHEMA, new ObjectMapper());
    }

    static IncidentParser parser() {
        return new IncidentParser(KNOWLEDGE_BASE);
    }

    static ContextRetriever retriever() {
        return new ContextRetriever(KNOWLEDGE_BASE, PROPERTIES);
    }

    static IncidentAgent agent(LlmClient llmClient) {
        return new IncidentAgent(
                parser(),
                retriever(),
                new PromptBuilder(KNOWLEDGE_BASE, SCHEMA),
                llmClient,
                validator(),
                new FallbackAnalyzer(KNOWLEDGE_BASE),
                PROPERTIES
        );
    }
}
