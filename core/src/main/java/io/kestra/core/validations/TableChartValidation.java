package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.TableChartValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TableChartValidator.class)
public @interface TableChartValidation {
    String message() default "invalid table chart";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
