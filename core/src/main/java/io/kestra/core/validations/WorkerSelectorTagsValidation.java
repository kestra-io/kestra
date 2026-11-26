package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.WorkerSelectorTagsValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.*;

/**
 * Constrains the literal tags of a worker selector to at most 20 RFC 1123 labels.
 * Tags holding a Pebble expression, or a list that is itself one, are only checked once rendered.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WorkerSelectorTagsValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER })
public @interface WorkerSelectorTagsValidation {
    String message() default "invalid worker selector tags";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
