package com.example.incidents.model;

import java.util.List;

public record IncidentAnalysis(String category, String summary, String severity, List<Hypothesis> hypotheses) {
}
