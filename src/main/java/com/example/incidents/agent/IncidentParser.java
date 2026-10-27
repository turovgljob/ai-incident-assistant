package com.example.incidents.agent;

import com.example.incidents.knowledge.KnowledgeBase;
import com.example.incidents.knowledge.PlatformComponent;
import com.example.incidents.knowledge.TextAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class IncidentParser {

    private static final Pattern STATUS_CODE = Pattern.compile("(?<![\\w.:/-])([45](?:\\d{2}|xx))(?![\\w.:])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(?<![\\d:])(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s?UTC)?(?![\\d:])", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENDPOINT = Pattern.compile("(?<![\\w/])/[a-z][\\w-]*(?:/[\\w{}-]+)*", Pattern.CASE_INSENSITIVE);

    private final KnowledgeBase knowledgeBase;

    public IncidentParser(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public ParsedIncident parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("Incident description must not be blank");
        }
        String text = rawText.strip().replace("\r\n", "\n");
        String lower = text.toLowerCase(Locale.ROOT);
        return new ParsedIncident(
                text,
                TextAnalysis.detectLanguage(text),
                mentioned(lower, PlatformComponent.Type.SERVICE),
                mentioned(lower, PlatformComponent.Type.EXTERNAL),
                matches(STATUS_CODE, text).map(code -> code.toLowerCase(Locale.ROOT)).distinct().toList(),
                matches(TIME, text).distinct().toList(),
                matches(ENDPOINT, text).distinct().toList(),
                TextAnalysis.keywords(text)
        );
    }

    private List<String> mentioned(String lowerText, PlatformComponent.Type type) {
        return knowledgeBase.components().stream()
                .filter(component -> component.type() == type)
                .filter(component -> component.aliases().stream().anyMatch(alias -> TextAnalysis.containsTerm(lowerText, alias)))
                .map(PlatformComponent::name)
                .toList();
    }

    private static Stream<String> matches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Stream.Builder<String> found = Stream.builder();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found.build();
    }
}
