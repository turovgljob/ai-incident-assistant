package com.example.incidents.agent;

import java.util.List;
import java.util.Set;

public record ParsedIncident(
        String text,
        String language,
        List<String> services,
        List<String> externalDependencies,
        List<String> statusCodes,
        List<String> times,
        List<String> endpoints,
        Set<String> keywords
) {
}
