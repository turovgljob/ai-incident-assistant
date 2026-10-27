package com.example.incidents.agent;

import com.example.incidents.agent.IncidentContext.CategoryMatch;
import com.example.incidents.knowledge.CategoryDefinition;
import com.example.incidents.knowledge.KnowledgeBase;
import com.example.incidents.model.IncidentAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FallbackAnalyzer {

    private final KnowledgeBase knowledgeBase;

    public FallbackAnalyzer(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public IncidentAnalysis analyze(ParsedIncident incident, IncidentContext context) {
        Optional<CategoryMatch> match = context.topCategory();
        CategoryDefinition category = match
                .flatMap(top -> knowledgeBase.findCategory(top.category()))
                .orElseGet(knowledgeBase::otherCategory);
        return new IncidentAnalysis(
                category.name(),
                summary(incident, match),
                category.defaultSeverity(),
                List.of(category.fallbackHypothesis())
        );
    }

    private static String summary(ParsedIncident incident, Optional<CategoryMatch> match) {
        String signals = match
                .map(top -> "Rule-based triage matched the signals " + top.matchedSignals() + ".")
                .orElse("No known incident pattern matched.");
        String services = incident.services().isEmpty()
                ? ""
                : " Mentioned services: " + String.join(", ", incident.services()) + ".";
        return "LLM analysis was unavailable, so this is a keyword-based classification. "
                + signals + services + " Verify the category and severity manually.";
    }
}
