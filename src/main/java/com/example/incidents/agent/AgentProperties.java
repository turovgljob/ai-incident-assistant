package com.example.incidents.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("incident.agent")
public record AgentProperties(int maxAttempts, int similarIncidents) {

    public AgentProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("incident.agent.max-attempts must be at least 1");
        }
    }
}
