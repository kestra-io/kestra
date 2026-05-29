package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.validations.validator.Rfc1123LabelValidator;

import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class Rfc1123LabelTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Getter
    public static class LabelHolder {
        @Rfc1123Label
        String value;
    }

    @Test
    void shouldAcceptValidLabels() {
        for (String ok : new String[]{
            "a", "ab", "a1", "1a",
            "docker", "linux-amd64", "us-east-1",
            "a" + "-".repeat(62) + "b" // length 64, dashes in middle
        }) {
            assertThat(modelValidator.isValid(new LabelHolder(ok)))
                .as("expected '%s' to be a valid RFC 1123 label", ok)
                .isEmpty();
            assertThat(Rfc1123LabelValidator.isValid(ok))
                .as("static isValid('%s')", ok)
                .isTrue();
        }
    }

    @Test
    void shouldRejectInvalidLabels() {
        String tooLong = "a".repeat(Rfc1123LabelValidator.MAX_LENGTH + 1);
        for (String bad : new String[]{
            "",          // empty
            "-a",        // leading hyphen
            "a-",        // trailing hyphen
            "a_b",       // underscore not allowed
            "A",         // uppercase not allowed
            "Aa",        // uppercase not allowed
            "a b",       // space not allowed
            "a.b",       // dot not allowed
            tooLong      // > MAX_LENGTH chars
        }) {
            assertThat(modelValidator.isValid(new LabelHolder(bad)))
                .as("expected '%s' to be rejected", bad)
                .isPresent();
            assertThat(Rfc1123LabelValidator.isValid(bad))
                .as("static isValid('%s')", bad)
                .isFalse();
        }
    }

    @Test
    void shouldAcceptNullViaAnnotation() {
        // Bean Validation contract: null is left to @NotNull; the label
        // constraint itself does not reject null.
        assertThat(modelValidator.isValid(new LabelHolder(null))).isEmpty();
    }

    @Test
    void staticIsValidShouldRejectNull() {
        // The static helper is used inside compact constructors where null is
        // never a valid identifier — keep it strict.
        assertThat(Rfc1123LabelValidator.isValid(null)).isFalse();
    }
}
