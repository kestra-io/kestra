package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.OutputValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OutputValidator.class)
public @interface OutputValidation {
    String message() default "invalid output";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
