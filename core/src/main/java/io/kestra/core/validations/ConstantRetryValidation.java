package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.ConstantRetryValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConstantRetryValidator.class)
public @interface ConstantRetryValidation {
    String message() default "invalid constant retry";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
