package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.WindowValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WindowValidator.class)
public @interface WindowValidation {
    String message() default "invalid window definition";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}