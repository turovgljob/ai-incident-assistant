package com.example.incidents.agent;

import com.example.incidents.agent.IncidentContext.CategoryMatch;
import com.example.incidents.agent.IncidentContext.SimilarIncident;
import com.example.incidents.knowledge.KnowledgeBase;
import com.example.incidents.knowledge.PastIncident;
import com.example.incidents.knowledge.TextAnalysis;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ContextRetriever {

    private static final double SAME_CATEGORY_BONUS = 0.2;
    private static final double MIN_SIMILARITY = 0.1;

    private final KnowledgeBase knowledgeBase;
    private final AgentProperties properties;

    public ContextRetriever(KnowledgeBase knowledgeBase, AgentProperties properties) {
        this.knowledgeBase = knowledgeBase;
        this.properties = properties;
    }

    public IncidentContext retrieve(ParsedIncident incident) {
        List<CategoryMatch> candidates = rankCategories(incident);
        Optional<String> topCategory = candidates.stream().findFirst().map(CategoryMatch::category);
        List<SimilarIncident> similar = knowledgeBase.pastIncidents().stream()
                .map(past -> new SimilarIncident(past, similarity(incident, past, topCategory)))
                .filter(candidate -> candidate.score() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(SimilarIncident::score).reversed())
                .limit(properties.similarIncidents())
                .toList();
        return new IncidentContext(candidates, similar);
    }

    private List<CategoryMatch> rankCategories(ParsedIncident incident) {
        String lower = incident.text().toLowerCase(Locale.ROOT);
        return knowledgeBase.categories().stream()
                .map(category -> new CategoryMatch(category.name(), category.signals().stream()
                        .filter(signal -> TextAnalysis.containsTerm(lower, signal))
                        .toList()))
                .filter(match -> !match.matchedSignals().isEmpty())
                .sorted(Comparator.comparingInt((CategoryMatch match) -> match.matchedSignals().size()).reversed())
                .toList();
    }

    private static double similarity(ParsedIncident incident, PastIncident past, Optional<String> topCategory) {
        double lexical = TextAnalysis.jaccard(incident.keywords(), TextAnalysis.keywords(past.text()));
        boolean sameCategory = topCategory.filter(past.category()::equals).isPresent();
        return lexical + (sameCategory ? SAME_CATEGORY_BONUS : 0);
    }
}
