package com.example.incidents.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

public class FakeLlmClient implements LlmClient {

    private final Deque<Supplier<String>> script = new ArrayDeque<>();
    private final List<LlmRequest> requests = new ArrayList<>();

    public FakeLlmClient reply(String... responses) {
        for (String response : responses) {
            script.add(() -> response);
        }
        return this;
    }

    public FakeLlmClient fail(String message) {
        script.add(() -> {
            throw new LlmException(message);
        });
        return this;
    }

    public List<LlmRequest> requests() {
        return requests;
    }

    public void reset() {
        script.clear();
        requests.clear();
    }

    @Override
    public String complete(LlmRequest request) {
        requests.add(request);
        Supplier<String> next = script.poll();
        if (next == null) {
            throw new IllegalStateException("FakeLlmClient has no scripted response left");
        }
        return next.get();
    }
}
