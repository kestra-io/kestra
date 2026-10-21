package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
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
    void shouldReportOneViolationPerOffendingElementWhenValueIsAnArrayOfObjects() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("disks")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" },
                      "size_gb": { "type": "integer", "maximum": 2048 }
                    }
                  }
                }
                """)
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate(List.of(
                Map.of("name", "root", "size_gb", 10),
                Map.of("name", "logs", "size_gb", 4096),
                Map.of("name", 42, "size_gb", 20)
            ))
        );

        // Then each offending element is reported where it sits, rather than collapsed into one message.
        assertThat(exception.getConstraintViolations())
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactlyInAnyOrder("disks[1].size_gb", "disks[2].name");
    }

    @Test
    void shouldNotBlameTheSchemaWhenOnlyTheValueIsWrong() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("payload")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": { "age": { "type": "integer" } }
                }
                """)
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate(Map.of("age", "not a number"))
        );

        // Then a valid schema is not reported as invalid just because the value failed it.
        assertThat(exception.getMessage()).doesNotContain("Invalid JSON schema");
    }

    @Test
    void shouldLocateTheInputItselfWhenTheWholeValueIsWrong() {
        // Given
        JsonInput input = JsonInput.builder()
            .id("disks")
            .jsonSchema("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "array"
                }
                """)
            .build();

        // When
        ConstraintViolationException exception = assertThrows(
            ConstraintViolationException.class,
            () -> input.validate(Map.of("not", "an array"))
        );

        // Then the path is the input, so a form shows the message above the field rather than inside it.
        assertThat(exception.getConstraintViolations())
            .extracting(violation -> violation.getPropertyPath().toString())
            .containsExactly("disks");
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
