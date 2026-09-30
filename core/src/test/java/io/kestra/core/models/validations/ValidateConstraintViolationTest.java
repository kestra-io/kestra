package io.kestra.core.models.validations;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateConstraintViolationTest {
    @Test
    void shouldExposeOneErrorPerLineWhenOnlyConstraintsAreSet() {
        ValidateConstraintViolation result = ValidateConstraintViolation.builder()
            .constraints("id: must not be null\n\ntype: must not be null\n")
            .build();

        assertThat(result.getErrors()).containsExactly(
            ValidationError.of("id: must not be null"),
            ValidationError.of("type: must not be null")
        );
    }

    @Test
    void shouldKeepLocatedErrorsOverTheConstraintsText() {
        ValidationError located = new ValidationError("must not be null", "/tasks/0/message", "tasks[log].message");
        ValidateConstraintViolation result = ValidateConstraintViolation.builder()
            .constraints("Validation error: tasks[log].message: must not be null")
            .errors(List.of(located))
            .build();

        assertThat(result.getErrors()).containsExactly(located);
    }

    @Test
    void shouldExposeNoErrorsWhenValid() {
        assertThat(ValidateConstraintViolation.builder().build().getErrors()).isEmpty();
    }
}
