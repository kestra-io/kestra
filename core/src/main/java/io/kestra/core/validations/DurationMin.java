package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DurationMinValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Rejects a {@link java.time.Duration} smaller than {@link #min()} (default zero, so negative durations).
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationMinValidator.class)
public @interface DurationMin {
    /** Minimum allowed duration, as an ISO-8601 string. Defaults to zero. */
    String min() default "PT0S";

    String message() default "duration must be at least {min}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
