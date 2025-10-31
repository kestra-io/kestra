package io.kestra.core.validations;

import io.kestra.core.validations.validator.InputValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InputValidator.class)
public @interface InputValidation {
    String message() default "invalid input";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
