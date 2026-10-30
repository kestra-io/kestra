package io.kestra.core.validations;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.filters.Regex;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class SafeRegexValidationTest {
    @Inject
    private ModelValidator modelValidator;

    enum TestField {
        NAMESPACE
    }

    @Test
    void shouldValidateSafeRegex() {
        // Given
        Regex<TestField> filter = Regex.<TestField> builder()
            .field(TestField.NAMESPACE)
            .value("io\\.kestra\\..*")
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(filter);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateCatastrophicRegex() {
        // Given
        Regex<TestField> filter = Regex.<TestField> builder()
            .field(TestField.NAMESPACE)
            .value("(a+)+")
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(filter);

        // Then
        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getMessage()).contains("catastrophic backtracking");
    }

    @Test
    void shouldNotValidateInvalidRegexSyntax() {
        // Given
        Regex<TestField> filter = Regex.<TestField> builder()
            .field(TestField.NAMESPACE)
            .value("[a-")
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(filter);

        // Then
        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getMessage()).contains("invalid regex pattern");
        assertThat(valid.get().getMessage()).contains("[a-");
    }

    @Test
    void shouldValidateSafeInputValidator() {
        StringInput input = StringInput.builder().id("ip").type(Type.STRING).validator("^(\\d{1,3}\\.){3}\\d{1,3}$").build();

        assertThat(modelValidator.isValid(input)).isEmpty();
    }

    @Test
    void shouldNotValidateCatastrophicInputValidator() {
        StringInput input = StringInput.builder().id("name").type(Type.STRING).validator("(a+)+$").build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(input);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("catastrophic backtracking");
    }

    @Test
    void shouldNotValidateInvalidSyntaxInputValidator() {
        StringInput input = StringInput.builder().id("name").type(Type.STRING).validator("[a-").build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(input);

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("invalid regex pattern");
    }
}
