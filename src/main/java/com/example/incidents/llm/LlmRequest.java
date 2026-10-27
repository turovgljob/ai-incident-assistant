package com.example.incidents.llm;

import java.util.List;

public record LlmRequest(String system, List<LlmMessage> messages) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }
}
