package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.RandomRetryValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RandomRetryValidator.class)
public @interface RandomRetryValidation {
    String message() default "invalid random retry";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
