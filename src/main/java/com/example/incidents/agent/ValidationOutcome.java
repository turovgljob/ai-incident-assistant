package com.example.incidents.agent;

import com.example.incidents.model.IncidentAnalysis;

import java.util.List;
import java.util.Optional;

public record ValidationOutcome(Optional<IncidentAnalysis> analysis, List<String> errors, List<String> repairs) {

    static ValidationOutcome valid(IncidentAnalysis analysis, List<String> repairs) {
        return new ValidationOutcome(Optional.of(analysis), List.of(), List.copyOf(repairs));
    }

    static ValidationOutcome invalid(List<String> errors, List<String> repairs) {
        return new ValidationOutcome(Optional.empty(), List.copyOf(errors), List.copyOf(repairs));
    }

    static ValidationOutcome invalid(String error) {
        return invalid(List.of(error), List.of());
    }

    public boolean isValid() {
        return analysis.isPresent();
    }
}
