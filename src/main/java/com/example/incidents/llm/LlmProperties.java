package com.example.incidents.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("incident.llm")
public record LlmProperties(
        String apiKey,
        String baseUrl,
        String model,
        String effort,
        long maxTokens,
        Duration timeout,
        boolean serverSideFallback
) {
}
