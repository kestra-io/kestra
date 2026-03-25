package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class RegexTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Getter
    public static class RegexCls {
        @Regex
        String pattern;
    }

    @Test
    void inputValidation() {
        final RegexCls validRegex = new RegexCls("[A-Z]+");

        assertThat(modelValidator.isValid(validRegex).isEmpty()).isTrue();

        final RegexCls invalidRegex = new RegexCls("\\");

        assertThat(modelValidator.isValid(invalidRegex).isPresent()).isTrue();
        assertThat(modelValidator.isValid(invalidRegex).get().getMessage()).contains("invalid pattern");
    }
}
