package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.Rfc1123LabelValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.*;

/**
 * Constrains a string to a RFC 1123 hostname label: lowercase alphanumerics
 * and hyphens, must start and end with an alphanumeric, max 64 characters.
 *
 * <p>Used as a single source of truth for label-like identifiers such as
 * Worker Queue ids, Worker Group ids, and routing tags.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Rfc1123LabelValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface Rfc1123Label {
    String message() default "must be an RFC 1123 label (lowercase alphanumerics and hyphens, "
        + "must start and end with alphanumeric, max 64 chars), got '${validatedValue}'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
