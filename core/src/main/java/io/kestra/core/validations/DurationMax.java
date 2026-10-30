package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DurationMaxValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Rejects a {@link java.time.Duration} larger than {@link #max()} (default 10 years).
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationMaxValidator.class)
public @interface DurationMax {
    /** Maximum allowed duration, as an ISO-8601 string. Defaults to 10 years. */
    String max() default "P3650D";

    String message() default "duration must not exceed {max}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
