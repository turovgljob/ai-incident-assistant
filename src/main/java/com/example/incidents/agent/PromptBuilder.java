package com.example.incidents.agent;

import com.example.incidents.agent.IncidentContext.CategoryMatch;
import com.example.incidents.agent.IncidentContext.SimilarIncident;
import com.example.incidents.knowledge.CategoryDefinition;
import com.example.incidents.knowledge.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    private final String systemPrompt;

    public PromptBuilder(KnowledgeBase knowledgeBase, AnalysisSchema schema) {
        this.systemPrompt = buildSystemPrompt(knowledgeBase, schema);
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String analysisRequest(ParsedIncident incident, IncidentContext context) {
        return """
                <incident>
                %s
                </incident>

                Facts extracted by a deterministic parser (may be incomplete):
                - Input language: %s
                - Mentioned services: %s
                - External dependencies: %s
                - HTTP status codes: %s
                - Times: %s
                - Endpoints: %s

                Keyword-based category candidates (hints only, verify them against the incident text):
                %s

                Similar past incidents:
                %s

                Return the analysis JSON for this incident.
                """.formatted(
                incident.text(),
                incident.language(),
                listOrNone(incident.services()),
                listOrNone(incident.externalDependencies()),
                listOrNone(incident.statusCodes()),
                listOrNone(incident.times()),
                listOrNone(incident.endpoints()),
                describeCandidates(context.candidateCategories()),
                describeSimilar(context.similarIncidents())
        );
    }

    public String correctionRequest(List<String> errors) {
        return """
                Your previous response was rejected by the validator:
                %s

                Return the corrected analysis as a single JSON object that satisfies the schema. \
                Output only the JSON object, with every text value in English.
                """.formatted(errors.stream().map(error -> "- " + error).collect(Collectors.joining("\n")));
    }

    private static String buildSystemPrompt(KnowledgeBase knowledgeBase, AnalysisSchema schema) {
        String categories = knowledgeBase.categories().stream()
                .map(PromptBuilder::describeCategory)
                .collect(Collectors.joining("\n"));
        return """
                You are an incident triage assistant for on-call engineers of a payment platform. \
                Given an incident description, you classify it, summarise it, rate its severity and propose \
                root-cause hypotheses with concrete diagnostic steps.

                <platform>
                %s
                </platform>

                Allowed categories (copy the name exactly):
                %s

                Severity scale:
                - high: a core customer flow (payments, login, balances) fails or is severely degraded for many users, or money may be lost.
                - medium: partial degradation, a secondary flow (notifications, reports) is broken, or only some users are affected.
                - low: no visible customer impact, internal tooling only, or a single isolated case.

                Response rules:
                - Respond with one JSON object only: no markdown, no code fences, no text before or after it.
                - The object must conform to this JSON Schema:
                %s
                - Write every text value in English, even when the incident is described in another language.
                - "summary" says what is happening, who is likely affected and why the severity was chosen, in 1-3 sentences.
                - Give 1 to 3 hypotheses, most likely first. Each has 2 or 3 next steps that name the concrete service, \
                log query, metric or dashboard to check.
                - Rely on the incident text, the platform description and similar past incidents. \
                Do not invent services that are not part of the platform.
                - If no category fits, use "%s".
                """.formatted(
                knowledgeBase.systemDescription().strip(),
                categories,
                schema.text().strip(),
                KnowledgeBase.OTHER_CATEGORY
        );
    }

    private static String describeCategory(CategoryDefinition category) {
        return "- " + category.name() + ": " + category.description();
    }

    private static String describeCandidates(List<CategoryMatch> candidates) {
        if (candidates.isEmpty()) {
            return "- none matched";
        }
        return candidates.stream()
                .map(match -> "- " + match.category() + " (signals: " + String.join(", ", match.matchedSignals()) + ")")
                .collect(Collectors.joining("\n"));
    }

    private static String describeSimilar(List<SimilarIncident> similar) {
        if (similar.isEmpty()) {
            return "- none found";
        }
        return similar.stream()
                .map(match -> "- [%s] category: %s. %s".formatted(
                        match.incident().id(), match.incident().category(), match.incident().text()))
                .collect(Collectors.joining("\n"));
    }

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
