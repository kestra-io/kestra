package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DurationPositiveValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Rejects a {@link java.time.Duration} that is negative or zero.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationPositiveValidator.class)
public @interface DurationPositive {
    String message() default "duration must be positive";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
