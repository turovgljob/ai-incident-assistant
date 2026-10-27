package com.example.incidents.agent;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonExtractor {

    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private JsonExtractor() {
    }

    static Optional<String> extractObject(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher fence = CODE_FENCE.matcher(raw);
        String text = fence.find() ? fence.group(1) : raw;
        int start = text.indexOf('{');
        if (start < 0) {
            return Optional.empty();
        }
        int end = matchingBrace(text, start);
        return Optional.of(end < 0 ? text.substring(start) : text.substring(start, end + 1));
    }

    private static int matchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
