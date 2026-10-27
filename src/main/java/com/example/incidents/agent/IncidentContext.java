package com.example.incidents.agent;

import com.example.incidents.knowledge.PastIncident;

import java.util.List;
import java.util.Optional;

public record IncidentContext(List<CategoryMatch> candidateCategories, List<SimilarIncident> similarIncidents) {

    public Optional<CategoryMatch> topCategory() {
        return candidateCategories.stream().findFirst();
    }

    public record CategoryMatch(String category, List<String> matchedSignals) {
    }

    public record SimilarIncident(PastIncident incident, double score) {
    }
}
