package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DataChartKPIValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataChartKPIValidator.class)
public @interface DataChartKPIValidation {
    String message() default "invalid data chart";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
