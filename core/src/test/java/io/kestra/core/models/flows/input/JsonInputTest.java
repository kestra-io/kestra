package io.kestra.core.models.flows.input;

import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonInputTest {
    @Test
    void shouldValidateInputAgainstSchema() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["name"],
                  "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                  },
                  "additionalProperties": false
                }
                """)
            .build();

        // When / Then
        assertDoesNotThrow(() -> input.validate(Map.of("name", "kestra", "age", 3)));
    }

    @Test
    void shouldFailWhenInputDoesNotMatchSchema() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["name"],
                  "properties": {
                    "name": { "type": "string" }
                  },
                  "additionalProperties": false
                }
                """)
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate(Map.of("unknown", "value"))
        );

        // Then
        assertTrue(exception.getMessage().contains("it must match the json schema"));
    }

    @Test
    void shouldFailWhenSchemaIsInvalidJson() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("{ this-is-not-json")
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate(Map.of("name", "kestra"))
        );

        // Then
        assertTrue(exception.getMessage().contains("Invalid JSON content or schema"));
    }

    @Test
    void shouldValidateWhenInputIsAJsonString() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "required": ["name"],
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """)
            .build();

        // When / Then
        assertDoesNotThrow(() -> input.validate("{\"name\":\"kestra\"}"));
    }

    @Test
    void shouldFailWhenInputStringIsInvalidJson() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """)
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate("{not-json}")
        );

        // Then
        assertTrue(exception.getMessage().contains("Invalid JSON content or schema"));
    }

    @Test
    void shouldSkipValidationWhenSchemaIsBlank() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("   ")
            .build();

        // When / Then
        assertDoesNotThrow(() -> input.validate(Map.of("anything", true)));
    }
}
