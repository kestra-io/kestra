package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.TenantIdValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.*;

/**
 * Constrains a string to a tenant identifier: lowercase alphanumerics, underscores and hyphens,
 * must start with an alphanumeric, max 100 characters.
 *
 * <p>
 * Used as a single source of truth everywhere a tenant id is carried, so the format is declared
 * once rather than repeated as a literal pattern on every model that has a {@code tenantId}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TenantIdValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface TenantId {
    String message() default "must be a tenant id (lowercase alphanumerics, underscores and hyphens, "
        + "must start with an alphanumeric, max 100 chars), got '${validatedValue}'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
