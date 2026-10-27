package com.example.incidents.knowledge;

import com.example.incidents.model.Hypothesis;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CategoryDefinition(
        String name,
        String description,
        String defaultSeverity,
        List<String> signals,
        Hypothesis fallbackHypothesis
) {
}
