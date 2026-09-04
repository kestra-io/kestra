package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.validations.validator.TenantIdValidator;

import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TenantIdTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Getter
    public static class TenantIdHolder {
        @TenantId
        String value;
    }

    @Test
    void shouldAcceptValidTenantIds() {
        for (String ok : new String[] {
            "a", "1", "main",
            "acme_corp", "acme-corp", "acme-corp_2",
            "a".repeat(TenantIdValidator.MAX_LENGTH)
        }) {
            assertThat(modelValidator.isValid(new TenantIdHolder(ok)))
                .as("expected '%s' to be a valid tenant id", ok)
                .isEmpty();
            assertThat(TenantIdValidator.isValid(ok))
                .as("static isValid('%s')", ok)
                .isTrue();
        }
    }

    @Test
    void shouldRejectInvalidTenantIds() {
        for (String bad : new String[] {
            "", // empty
            "_a", // must start with an alphanumeric
            "-a", // must start with an alphanumeric
            "Acme", // uppercase not allowed
            "acme corp", // space not allowed
            "acme.corp", // dot not allowed
            "acme|corp", // the uid separator must never appear in a tenant id
            "a".repeat(TenantIdValidator.MAX_LENGTH + 1) // wider than the narrowest tenant_id column
        }) {
            assertThat(modelValidator.isValid(new TenantIdHolder(bad)))
                .as("expected '%s' to be rejected", bad)
                .isPresent();
            assertThat(TenantIdValidator.isValid(bad))
                .as("static isValid('%s')", bad)
                .isFalse();
        }
    }

    @Test
    void shouldAcceptATenantIdLongerThanTwoCharacters() {
        // TriggerContext carried "^[a-z0-9][a-z0-9_-]" — no quantifier — and @Pattern matches the
        // whole value, so it accepted a two-character tenant id and nothing else.
        assertThat(modelValidator.isValid(new TenantIdHolder("main"))).isEmpty();
    }

    @Test
    void shouldAcceptNullViaAnnotation() {
        // Bean Validation contract: null is left to @NotNull; the constraint itself does not
        // reject null.
        assertThat(modelValidator.isValid(new TenantIdHolder(null))).isEmpty();
    }

    @Test
    void staticIsValidShouldRejectNull() {
        assertThat(TenantIdValidator.isValid(null)).isFalse();
    }
}
