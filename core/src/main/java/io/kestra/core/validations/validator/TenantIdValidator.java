package io.kestra.core.validations.validator;

import java.util.regex.Pattern;

import io.kestra.core.validations.TenantId;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/**
 * Validates that a value is a tenant identifier: lowercase alphanumerics, underscores and hyphens,
 * starting with an alphanumeric, at most {@value #MAX_LENGTH} characters.
 *
 * <p>
 * The bound matches the {@code tenant_id} columns, which are all {@code VARCHAR(250)}. Without it
 * an over-long tenant id is only rejected by the database, and a write that fails there can be one
 * the caller cannot retry past.
 *
 * <p>
 * Exposes {@link #isValid(String)} as a static helper so record compact constructors and other
 * non-CDI code paths can enforce the same invariant without going through the validator beans.
 */
@Singleton
public final class TenantIdValidator implements ConstraintValidator<TenantId, String> {

    public static final int MAX_LENGTH = 100;
    public static final String PATTERN = "^[a-z0-9][a-z0-9_-]*$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<TenantId> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value == null || isValid(value);
    }

    /**
     * Returns {@code true} if {@code value} is a non-null tenant id.
     */
    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && COMPILED.matcher(value).matches();
    }
}
