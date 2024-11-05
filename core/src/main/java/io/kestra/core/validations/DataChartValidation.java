package io.kestra.core.validations;

import io.kestra.core.validations.validator.DataChartValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataChartValidator.class)
public @interface DataChartValidation {
    String message() default "invalid Chart";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
