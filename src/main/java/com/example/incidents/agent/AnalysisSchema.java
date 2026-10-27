package com.example.incidents.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Component
public class AnalysisSchema {

    private static final String LOCATION = "schema/incident-analysis.schema.json";

    private final String text;
    private final JsonSchema schema;

    public AnalysisSchema() {
        try (InputStream in = new ClassPathResource(LOCATION).getInputStream()) {
            this.text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + LOCATION, e);
        }
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build();
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(text, config);
    }

    public String text() {
        return text;
    }

    public List<String> validate(JsonNode node) {
        return schema.validate(node).stream()
                .map(ValidationMessage::getMessage)
                .sorted()
                .toList();
    }
}
