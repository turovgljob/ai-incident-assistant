package com.example.incidents.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final LlmProperties properties;
    private final AnthropicClient client;

    public AnthropicLlmClient(LlmProperties properties) {
        this.properties = properties;
        this.client = StringUtils.hasText(properties.apiKey())
                ? AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .maxRetries(2)
                .build()
                : null;
        if (client == null) {
            log.warn("ANTHROPIC_API_KEY is not set: every request will be answered by the rule-based fallback");
        }
    }

    @Override
    public String complete(LlmRequest request) {
        if (client == null) {
            throw new LlmException("ANTHROPIC_API_KEY is not configured");
        }
        Message response;
        try {
            response = client.messages().create(toParams(request));
        } catch (AnthropicException e) {
            throw new LlmException("Anthropic API call failed: " + e.getMessage(), e);
        }
        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new LlmException("Model refused to answer");
        }
        if (response.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            log.warn("Model output was truncated by max_tokens={}", properties.maxTokens());
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }

    private MessageCreateParams toParams(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(request.system())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .build());
        if (properties.serverSideFallback()) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (LlmMessage message : request.messages()) {
            switch (message.role()) {
                case USER -> builder.addUserMessage(message.content());
                case ASSISTANT -> builder.addAssistantMessage(message.content());
            }
        }
        return builder.build();
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
