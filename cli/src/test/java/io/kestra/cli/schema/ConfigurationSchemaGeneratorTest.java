package io.kestra.cli.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationSchemaGeneratorTest {

    @Test
    void shouldGenerateSchemaWithRootStructure() {
        // Given
        var generator = new ConfigurationSchemaGenerator();

        // When
        ObjectNode schema = generator.generate(null);

        // Then
        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.get("title").asText()).isEqualTo("Kestra Configuration Schema");
        assertThat(schema.get("type").asText()).isEqualTo("object");
    }

    @Test
    void shouldDiscoverKestraConfigurationProperties() {
        // Given
        var generator = new ConfigurationSchemaGenerator();

        // When
        ObjectNode schema = generator.generate(null);

        // Then — at minimum, kestra.* properties should be discovered
        JsonNode properties = schema.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.has("kestra")).isTrue();
    }

    @Test
    void shouldSkipPluginStoragesWhenRegistryIsNull() {
        // Given
        var generator = new ConfigurationSchemaGenerator();

        // When
        ObjectNode schema = generator.generate(null);

        // Then — no crash, schema is valid
        assertThat(schema.get("type").asText()).isEqualTo("object");
    }

    @ParameterizedTest
    @CsvSource({
        "camelCase, camel-case",
        "myPropertyName, my-property-name",
        "simpleURL, simple-url",
        "HTMLParser, html-parser",
        "simple, simple",
        "a, a",
        "'', ''"
    })
    void shouldConvertToKebabCase(String input, String expected) {
        assertThat(ConfigurationSchemaGenerator.toKebabCase(input)).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullForNullKebabCase() {
        assertThat(ConfigurationSchemaGenerator.toKebabCase(null)).isNull();
    }
}
