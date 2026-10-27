package com.example.incidents.agent;

import com.example.incidents.knowledge.CategoryDefinition;
import com.example.incidents.knowledge.KnowledgeBase;
import com.example.incidents.knowledge.TextAnalysis;
import com.example.incidents.model.Hypothesis;
import com.example.incidents.model.IncidentAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ResponseValidator {

    private static final int MAX_HYPOTHESES = 3;
    private static final int MAX_NEXT_STEPS = 3;
    private static final Set<String> WRAPPER_KEYS = Set.of("analysis", "result", "incident", "data", "response");
    private static final Set<String> ROOT_FIELDS = Set.of("category", "summary", "severity", "hypotheses");
    private static final Set<String> HYPOTHESIS_FIELDS = Set.of("title", "reasoning", "next_steps");
    private static final Map<String, String> HYPOTHESIS_FIELD_ALIASES = Map.of(
            "nextSteps", "next_steps",
            "steps", "next_steps",
            "next-steps", "next_steps",
            "rationale", "reasoning",
            "name", "title"
    );
    private static final Map<String, String> SEVERITY_ALIASES = Map.of(
            "critical", "high",
            "major", "high",
            "moderate", "medium",
            "minor", "low"
    );

    private final KnowledgeBase knowledgeBase;
    private final AnalysisSchema schema;
    private final ObjectMapper objectMapper;

    public ResponseValidator(KnowledgeBase knowledgeBase, AnalysisSchema schema, ObjectMapper objectMapper) {
        this.knowledgeBase = knowledgeBase;
        this.schema = schema;
        this.objectMapper = objectMapper;
    }

    public ValidationOutcome validate(String rawResponse) {
        Optional<String> json = JsonExtractor.extractObject(rawResponse);
        if (json.isEmpty()) {
            return ValidationOutcome.invalid("The response does not contain a JSON object");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(json.get());
        } catch (JsonProcessingException e) {
            return ValidationOutcome.invalid("The response is not valid JSON: " + e.getOriginalMessage());
        }
        if (!(node instanceof ObjectNode root)) {
            return ValidationOutcome.invalid("The response must be a JSON object");
        }

        List<String> repairs = new ArrayList<>();
        ObjectNode analysisNode = autoCorrect(root, repairs);

        List<String> errors = new ArrayList<>(schema.validate(analysisNode));
        if (!errors.isEmpty()) {
            return ValidationOutcome.invalid(errors, repairs);
        }

        IncidentAnalysis analysis;
        try {
            analysis = objectMapper.treeToValue(analysisNode, IncidentAnalysis.class);
        } catch (JsonProcessingException e) {
            return ValidationOutcome.invalid(List.of("Cannot map the response: " + e.getOriginalMessage()), repairs);
        }
        errors.addAll(semanticErrors(analysis));
        return errors.isEmpty() ? ValidationOutcome.valid(analysis, repairs) : ValidationOutcome.invalid(errors, repairs);
    }

    private ObjectNode autoCorrect(ObjectNode root, List<String> repairs) {
        ObjectNode analysis = unwrap(root, repairs);
        dropUnknownFields(analysis, ROOT_FIELDS, "/", repairs);
        normalizeSeverity(analysis, repairs);
        normalizeCategory(analysis, repairs);
        if (analysis.get("hypotheses") instanceof ArrayNode hypotheses) {
            truncate(hypotheses, MAX_HYPOTHESES, "/hypotheses", repairs);
            for (int i = 0; i < hypotheses.size(); i++) {
                if (hypotheses.get(i) instanceof ObjectNode hypothesis) {
                    String path = "/hypotheses/" + i;
                    renameAliases(hypothesis, repairs);
                    dropUnknownFields(hypothesis, HYPOTHESIS_FIELDS, path, repairs);
                    if (hypothesis.get("next_steps") instanceof ArrayNode steps) {
                        truncate(steps, MAX_NEXT_STEPS, path + "/next_steps", repairs);
                    }
                }
            }
        }
        return analysis;
    }

    private static ObjectNode unwrap(ObjectNode root, List<String> repairs) {
        if (root.has("category") || root.size() != 1) {
            return root;
        }
        Map.Entry<String, JsonNode> only = root.properties().iterator().next();
        if (WRAPPER_KEYS.contains(only.getKey().toLowerCase(Locale.ROOT)) && only.getValue() instanceof ObjectNode inner) {
            repairs.add("Unwrapped the analysis from the '" + only.getKey() + "' field");
            return inner;
        }
        return root;
    }

    private static void renameAliases(ObjectNode node, List<String> repairs) {
        HYPOTHESIS_FIELD_ALIASES.forEach((alias, canonical) -> {
            if (node.has(alias) && !node.has(canonical)) {
                node.set(canonical, node.remove(alias));
                repairs.add("Renamed field '" + alias + "' to '" + canonical + "'");
            }
        });
    }

    private static void dropUnknownFields(ObjectNode node, Set<String> allowed, String path, List<String> repairs) {
        List<String> unknown = node.properties().stream()
                .map(Map.Entry::getKey)
                .filter(name -> !allowed.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            node.remove(unknown);
            repairs.add("Removed unexpected fields " + unknown + " at " + path);
        }
    }

    private static void normalizeSeverity(ObjectNode node, List<String> repairs) {
        JsonNode severity = node.get("severity");
        if (severity == null || !severity.isTextual()) {
            return;
        }
        String original = severity.asText();
        String normalized = original.strip().toLowerCase(Locale.ROOT);
        normalized = SEVERITY_ALIASES.getOrDefault(normalized, normalized);
        if (!normalized.equals(original)) {
            node.put("severity", normalized);
            repairs.add("Normalized severity '" + original + "' to '" + normalized + "'");
        }
    }

    private void normalizeCategory(ObjectNode node, List<String> repairs) {
        JsonNode category = node.get("category");
        if (category == null || !category.isTextual()) {
            return;
        }
        String original = category.asText();
        knowledgeBase.findCategory(original)
                .map(CategoryDefinition::name)
                .filter(canonical -> !canonical.equals(original))
                .ifPresent(canonical -> {
                    node.put("category", canonical);
                    repairs.add("Normalized category '" + original + "' to '" + canonical + "'");
                });
    }

    private static void truncate(ArrayNode array, int max, String path, List<String> repairs) {
        if (array.size() > max) {
            int original = array.size();
            while (array.size() > max) {
                array.remove(array.size() - 1);
            }
            repairs.add("Truncated " + path + " from " + original + " to " + max + " items");
        }
    }

    private List<String> semanticErrors(IncidentAnalysis analysis) {
        List<String> errors = new ArrayList<>();
        if (knowledgeBase.findCategory(analysis.category()).isEmpty()) {
            String allowed = knowledgeBase.categories().stream()
                    .map(category -> "\"" + category.name() + "\"")
                    .collect(Collectors.joining(", "));
            errors.add("/category: \"" + analysis.category() + "\" is not one of the allowed categories: " + allowed);
        }
        String language = TextAnalysis.detectLanguage(allText(analysis));
        if (!TextAnalysis.ENGLISH.equals(language)) {
            errors.add("Text fields must be written in English, but the detected language is '" + language + "'");
        }
        return errors;
    }

    private static String allText(IncidentAnalysis analysis) {
        return Stream.concat(
                        Stream.of(analysis.summary()),
                        analysis.hypotheses().stream().flatMap(ResponseValidator::hypothesisText))
                .collect(Collectors.joining(" "));
    }

    private static Stream<String> hypothesisText(Hypothesis hypothesis) {
        return Stream.concat(Stream.of(hypothesis.title(), hypothesis.reasoning()), hypothesis.nextSteps().stream());
    }
}
