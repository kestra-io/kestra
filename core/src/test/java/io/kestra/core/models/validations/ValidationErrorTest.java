package io.kestra.core.models.validations;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationErrorTest {
    @Test
    void shouldKeepTheMessageWhenTheExceptionCarriesNoViolation() {
        ConstraintViolationException e = new ConstraintViolationException("Illegal flow source: boom", Set.of());

        assertThat(ValidationError.ofException(e)).containsExactly(ValidationError.of("Illegal flow source: boom"));
    }
}
