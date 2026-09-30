package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DurationMinValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Rejects a {@link java.time.Duration} shorter than {@link #min()} (default 1 millisecond, so a
 * bare {@code @DurationMin} rejects zero and negative durations).
 *
 * <p>
 * Prefer this over Hibernate's identically named {@code @DurationMin}: Micronaut resolves only its
 * own constraint validators when it constructs an {@code @Introspected} type from JSON, so the
 * Hibernate constraint raises {@code UnexpectedTypeException} there and fails deserialization for
 * every payload rather than only the invalid ones.
 * </p>
 *
 * <p>
 * A {@link #min()} other than the default only applies where Micronaut validates the constructor
 * of an {@code @Introspected} type, such as an HTTP request body. Validation delegated to Hibernate
 * ({@code Validator.validate(bean)}, and so {@code ModelValidator} and every flow model) receives
 * no annotation members and always applies the default, silently. Use the bare form outside a
 * request body, as {@link DurationMax} does everywhere it appears.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DurationMinValidator.class)
public @interface DurationMin {
    /** Minimum allowed duration, as an ISO-8601 string. Defaults to 1 millisecond. */
    String min() default "PT0.001S";

    String message() default "duration must be at least {min}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
