package io.kestra.core.models.validations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateConstraintViolationTest {
    @Test
    void shouldExposeNoErrorsWhenValid() {
        assertThat(ValidateConstraintViolation.builder().build().getErrors()).isEmpty();
    }
}
