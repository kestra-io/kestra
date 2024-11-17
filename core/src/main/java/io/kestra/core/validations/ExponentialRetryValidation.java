package io.kestra.core.validations;

import io.kestra.core.validations.validator.ExponentialRetryValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExponentialRetryValidator.class)
public @interface ExponentialRetryValidation {
    String message() default "invalid exponential retry";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
