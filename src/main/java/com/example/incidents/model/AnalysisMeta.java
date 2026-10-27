package com.example.incidents.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisMeta(
        Source source,
        int attempts,
        String inputLanguage,
        List<String> similarIncidents,
        List<String> repairs,
        List<String> errors
) {

    public enum Source {
        @JsonProperty("llm")
        LLM,
        @JsonProperty("fallback")
        FALLBACK
    }
}
