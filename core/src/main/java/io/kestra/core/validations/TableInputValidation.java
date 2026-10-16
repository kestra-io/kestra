package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.TableInputValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TableInputValidator.class)
public @interface TableInputValidation {
    String message() default "invalid table input";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
