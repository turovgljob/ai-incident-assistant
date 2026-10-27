package com.example.incidents.knowledge;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TextAnalysis {

    public static final String ENGLISH = "en";
    public static final String RUSSIAN = "ru";
    public static final String UNKNOWN = "unknown";

    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "have", "has", "not", "but",
            "all", "any", "some", "many", "via", "into", "out", "our", "their", "they", "see", "show", "shows",
            "other", "around", "about", "logs", "log", "service", "services", "users", "customers", "issue",
            "это", "что", "как", "для", "при", "или", "все", "нет", "так", "уже", "они", "его", "сервис", "логах"
    );

    private TextAnalysis() {
    }

    public static String detectLanguage(String text) {
        long latin = 0;
        long cyrillic = 0;
        long otherLetters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            if (script == Character.UnicodeScript.LATIN) {
                latin++;
            } else if (script == Character.UnicodeScript.CYRILLIC) {
                cyrillic++;
            } else {
                otherLetters++;
            }
        }
        long letters = latin + cyrillic + otherLetters;
        if (letters == 0) {
            return UNKNOWN;
        }
        if (cyrillic > letters * 0.2) {
            return RUSSIAN;
        }
        if (latin > letters * 0.8) {
            return ENGLISH;
        }
        return UNKNOWN;
    }

    public static Set<String> keywords(String text) {
        return Arrays.stream(TOKEN_SEPARATOR.split(text.toLowerCase(Locale.ROOT)))
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOPWORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean containsTerm(String lowerCaseText, String term) {
        String pattern = "(?<![\\p{L}\\p{N}])" + Pattern.quote(term.toLowerCase(Locale.ROOT));
        return Pattern.compile(pattern).matcher(lowerCaseText).find();
    }

    public static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        long intersection = left.stream().filter(right::contains).count();
        long union = left.size() + right.size() - intersection;
        return (double) intersection / union;
    }
}
