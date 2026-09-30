package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DashboardQueryTimeoutValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DashboardQueryTimeoutValidator.class)
public @interface DashboardQueryTimeoutValidation {
    String message() default "invalid query timeout";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
