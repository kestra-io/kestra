package io.kestra.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.core.io.ResourceResolver;

import static org.assertj.core.api.Assertions.assertThat;

class OpenapiTest {

    @Test
    void generatedOpenapiSpecFile() {
        Optional<URL> openapiSpec = new ResourceResolver().getResource("classpath:META-INF/swagger/kestra.yml");

        assertThat(openapiSpec.isPresent()).isTrue();
    }

    @Test
    void shouldRequireOutputValueForFlowButNotForFlowForExecution() throws IOException {
        JsonNode schemas = spec().path("components").path("schemas");

        assertThat(outputItems(schemas, "FlowForExecution")).isNotEmpty().allSatisfy(items -> assertThat(required(schemas, items)).doesNotContain("value"));
        assertThat(outputItems(schemas, "Flow")).isNotEmpty().allSatisfy(items -> assertThat(required(schemas, items)).contains("value"));
    }

    private static JsonNode spec() throws IOException {
        try (InputStream stream = new ResourceResolver().getResourceAsStream("classpath:META-INF/swagger/kestra.yml").orElseThrow()) {
            return JacksonMapper.ofYaml().readTree(stream);
        }
    }

    private static List<JsonNode> outputItems(JsonNode schemas, String schemaName) {
        List<JsonNode> items = new ArrayList<>();
        collect(schemas, schemas.path(schemaName), node -> node.path("properties").path("outputs").path("items"), items);
        return items;
    }

    private static List<String> required(JsonNode schemas, JsonNode schema) {
        List<JsonNode> nodes = new ArrayList<>();
        collect(schemas, schema, node -> node.path("required"), nodes);
        List<String> names = new ArrayList<>();
        nodes.forEach(required -> required.forEach(name -> names.add(name.asText())));
        return names;
    }

    /** Collects the extracted node from a schema and from every schema it inherits through {@code $ref} and {@code allOf}. */
    private static void collect(JsonNode schemas, JsonNode schema, Function<JsonNode, JsonNode> extractor, List<JsonNode> collected) {
        if (schema.has("$ref")) {
            collect(schemas, schemas.path(schema.get("$ref").asText().substring("#/components/schemas/".length())), extractor, collected);
            return;
        }
        JsonNode extracted = extractor.apply(schema);
        if (!extracted.isMissingNode()) {
            collected.add(extracted);
        }
        schema.path("allOf").forEach(parent -> collect(schemas, parent, extractor, collected));
    }
}
