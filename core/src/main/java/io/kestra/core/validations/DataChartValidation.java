package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DataChartValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataChartValidator.class)
public @interface DataChartValidation {
    String message() default "invalid data chart";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
