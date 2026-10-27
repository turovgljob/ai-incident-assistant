package com.example.incidents.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record AnalysisResult(@JsonUnwrapped IncidentAnalysis analysis, AnalysisMeta meta) {
}
