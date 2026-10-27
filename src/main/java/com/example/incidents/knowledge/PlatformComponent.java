package com.example.incidents.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PlatformComponent(String name, Type type, List<String> aliases) {

    public enum Type {
        @JsonProperty("service")
        SERVICE,
        @JsonProperty("external")
        EXTERNAL
    }
}
