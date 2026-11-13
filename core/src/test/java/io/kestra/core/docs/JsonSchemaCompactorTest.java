package io.kestra.core.docs;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaCompactorTest {
    private static final String LONG_DOC = "x".repeat(200);

    @Test
    @SuppressWarnings("unchecked")
    void shouldShareOnlyRepeatedPropertySchemasWhenCompacting() throws Exception {
        // Given: `retry` repeats, `timeout` shares a name but not a schema, `id` is too small to be worth a $ref
        Map<String, Object> schema = JacksonMapper.ofJson().readValue("""
            {
              "definitions": {
                "a.First": {"properties": {
                  "id": {"type": "string"},
                  "retry": {"type": "object", "title": "%1$s"},
                  "timeout": {"type": "string", "title": "%1$s"}
                }},
                "a.Second": {"properties": {
                  "id": {"type": "string"},
                  "retry": {"type": "object", "title": "%1$s"},
                  "timeout": {"type": "integer", "title": "%1$s"}
                }}
              }
            }
            """.formatted(LONG_DOC), Map.class);

        // When
        Map<String, Object> compact = JsonSchemaCompactor.compact(schema);

        // Then
        Map<String, Object> definitions = (Map<String, Object>) compact.get("definitions");
        assertThat(definitions).containsOnlyKeys("a.First", "a.Second", "shared-property-retry");
        assertThat(definitions.get("shared-property-retry")).isEqualTo(Map.of("type", "object", "title", LONG_DOC));
        for (String key : new String[] { "a.First", "a.Second" }) {
            Map<String, Object> properties = (Map<String, Object>) ((Map<String, Object>) definitions.get(key)).get("properties");
            assertThat(properties.get("retry")).isEqualTo(Map.of("$ref", "#/definitions/shared-property-retry"));
            assertThat(properties.get("id")).isEqualTo(Map.of("type", "string"));
            assertThat((Map<String, Object>) properties.get("timeout")).containsKey("title");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldLeaveLiteralValuesAloneWhenTheyLookLikeASchema() throws Exception {
        // Given: two defaults that are data, each holding a `properties` key with a repeated, large value
        Map<String, Object> schema = JacksonMapper.ofJson().readValue("""
            {
              "definitions": {
                "a.First": {"properties": {"spec": {"type": "object", "default": {"properties": {"doc": {"title": "%1$s"}}}}}},
                "a.Second": {"properties": {"other": {"type": "string", "default": {"properties": {"doc": {"title": "%1$s"}}}}}}
              }
            }
            """.formatted(LONG_DOC), Map.class);

        // When
        Map<String, Object> compact = JsonSchemaCompactor.compact(schema);

        // Then
        assertThat(compact).isEqualTo(schema);
    }
}
