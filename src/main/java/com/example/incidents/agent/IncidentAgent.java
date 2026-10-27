package com.example.incidents.agent;

import com.example.incidents.llm.LlmClient;
import com.example.incidents.llm.LlmException;
import com.example.incidents.llm.LlmMessage;
import com.example.incidents.llm.LlmRequest;
import com.example.incidents.model.AnalysisMeta;
import com.example.incidents.model.AnalysisResult;
import com.example.incidents.model.IncidentAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IncidentAgent {

    private static final Logger log = LoggerFactory.getLogger(IncidentAgent.class);

    private final IncidentParser parser;
    private final ContextRetriever retriever;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ResponseValidator validator;
    private final FallbackAnalyzer fallbackAnalyzer;
    private final AgentProperties properties;

    public IncidentAgent(
            IncidentParser parser,
            ContextRetriever retriever,
            PromptBuilder promptBuilder,
            LlmClient llmClient,
            ResponseValidator validator,
            FallbackAnalyzer fallbackAnalyzer,
            AgentProperties properties
    ) {
        this.parser = parser;
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.validator = validator;
        this.fallbackAnalyzer = fallbackAnalyzer;
        this.properties = properties;
    }

    public AnalysisResult analyze(String description) {
        ParsedIncident incident = parser.parse(description);
        IncidentContext context = retriever.retrieve(incident);
        List<String> similarIds = context.similarIncidents().stream()
                .map(similar -> similar.incident().id())
                .toList();
        log.info("Parsed incident: language={}, services={}, candidates={}, similar={}",
                incident.language(), incident.services(),
                context.candidateCategories().stream().map(IncidentContext.CategoryMatch::category).toList(),
                similarIds);

        List<LlmMessage> conversation = new ArrayList<>();
        conversation.add(LlmMessage.user(promptBuilder.analysisRequest(incident, context)));
        List<String> errors = new ArrayList<>();
        int attempt = 0;

        while (attempt < properties.maxAttempts()) {
            attempt++;
            String response;
            try {
                response = llmClient.complete(new LlmRequest(promptBuilder.systemPrompt(), conversation));
            } catch (LlmException e) {
                log.warn("Attempt {}: LLM call failed, switching to fallback: {}", attempt, e.getMessage());
                errors.add("attempt " + attempt + ": " + e.getMessage());
                break;
            }

            ValidationOutcome outcome = validator.validate(response);
            if (outcome.isValid()) {
                log.info("Attempt {}: response accepted, repairs={}", attempt, outcome.repairs());
                IncidentAnalysis analysis = outcome.analysis().orElseThrow();
                return result(analysis, AnalysisMeta.Source.LLM, attempt, incident, similarIds, outcome.repairs(), errors);
            }

            log.warn("Attempt {}: response rejected: {}", attempt, outcome.errors());
            for (String error : outcome.errors()) {
                errors.add("attempt " + attempt + ": " + error);
            }
            conversation.add(LlmMessage.assistant(response.isBlank() ? "(empty response)" : response));
            conversation.add(LlmMessage.user(promptBuilder.correctionRequest(outcome.errors())));
        }

        IncidentAnalysis fallback = fallbackAnalyzer.analyze(incident, context);
        return result(fallback, AnalysisMeta.Source.FALLBACK, attempt, incident, similarIds, List.of(), errors);
    }

    private static AnalysisResult result(
            IncidentAnalysis analysis,
            AnalysisMeta.Source source,
            int attempts,
            ParsedIncident incident,
            List<String> similarIds,
            List<String> repairs,
            List<String> errors
    ) {
        AnalysisMeta meta = new AnalysisMeta(source, attempts, incident.language(), similarIds, repairs, List.copyOf(errors));
        return new AnalysisResult(analysis, meta);
    }
}
