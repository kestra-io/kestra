package io.kestra.core.validations;

import io.kestra.core.validations.validator.KPIChartValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KPIChartValidator.class)
public @interface KPIChartValidation {
    String message() default "The KPI chart configuration is invalid.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
