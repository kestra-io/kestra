package io.kestra.core.validations;

import io.kestra.core.validations.validator.TimeSeriesChartValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeSeriesChartValidator.class)
public @interface TimeSeriesChartValidation {
    String message() default "invalid TimeSeries chart";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
