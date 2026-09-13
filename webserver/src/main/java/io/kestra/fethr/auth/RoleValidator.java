package io.kestra.fethr.auth;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/**
 * Validates the {@link Role} on a request body: it must be present. An unknown role name deserializes to
 * null (the field uses READ_UNKNOWN_ENUM_VALUES_AS_NULL), so both a missing and an unknown role are rejected
 * here as a clean 422, and Jackson never owns the rejection. Owner is accepted; only an owner may actually
 * assign it, which the controller enforces.
 */
@Singleton
@Introspected
public class RoleValidator implements ConstraintValidator<RoleCheck, Role> {

    @Override
    public boolean isValid(
        @Nullable Role value,
        @NonNull AnnotationValue<RoleCheck> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value != null;
    }
}
