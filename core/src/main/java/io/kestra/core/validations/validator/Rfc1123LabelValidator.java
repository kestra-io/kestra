package io.kestra.core.validations.validator;

import java.util.regex.Pattern;

import io.kestra.core.validations.Rfc1123Label;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/**
 * Validates that a value is a RFC 1123 hostname label: lowercase alphanumerics
 * and hyphens, must start and end with an alphanumeric, max {@value #MAX_LENGTH}
 * characters.
 *
 * <p>Exposes {@link #isValid(String)} as a static helper so record compact
 * constructors and other non-CDI code paths can enforce the same invariant
 * without going through the validator beans.
 */
@Singleton
public final class Rfc1123LabelValidator implements ConstraintValidator<Rfc1123Label, String> {

    public static final int MAX_LENGTH = 64;
    public static final String PATTERN = "^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<Rfc1123Label> annotationMetadata,
        @NonNull ConstraintValidatorContext context
    ) {
        return value == null || isValid(value);
    }

    /**
     * Returns {@code true} if {@code value} is a non-null RFC 1123 label.
     */
    public static boolean isValid(String value) {
        return value != null && value.length() <= MAX_LENGTH && COMPILED.matcher(value).matches();
    }
}
