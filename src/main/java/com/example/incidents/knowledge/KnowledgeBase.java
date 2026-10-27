package com.example.incidents.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class KnowledgeBase {

    public static final String OTHER_CATEGORY = "Other / unclassified";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String systemDescription;
    private final List<PlatformComponent> components;
    private final List<CategoryDefinition> categories;
    private final List<PastIncident> pastIncidents;

    public KnowledgeBase() {
        this.systemDescription = readText("knowledge/system-description.md");
        this.components = readJson("knowledge/components.json", new TypeReference<>() {
        });
        this.categories = readJson("knowledge/categories.json", new TypeReference<>() {
        });
        this.pastIncidents = readJson("knowledge/past-incidents.json", new TypeReference<>() {
        });
        if (findCategory(OTHER_CATEGORY).isEmpty()) {
            throw new IllegalStateException("categories.json must define '" + OTHER_CATEGORY + "'");
        }
    }

    public String systemDescription() {
        return systemDescription;
    }

    public List<PlatformComponent> components() {
        return components;
    }

    public List<CategoryDefinition> categories() {
        return categories;
    }

    public List<PastIncident> pastIncidents() {
        return pastIncidents;
    }

    public Optional<CategoryDefinition> findCategory(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.strip();
        return categories.stream()
                .filter(category -> category.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public CategoryDefinition otherCategory() {
        return findCategory(OTHER_CATEGORY).orElseThrow();
    }

    static String readText(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read classpath resource " + path, e);
        }
    }

    private static <T> T readJson(String path, TypeReference<T> type) {
        try {
            return MAPPER.readValue(readText(path), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot parse classpath resource " + path, e);
        }
    }
}
