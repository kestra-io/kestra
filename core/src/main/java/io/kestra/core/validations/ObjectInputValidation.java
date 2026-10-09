package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.ObjectInputValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ObjectInputValidator.class)
public @interface ObjectInputValidation {
    String message() default "invalid object input";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
