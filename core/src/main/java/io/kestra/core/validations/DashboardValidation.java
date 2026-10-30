package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DashboardValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DashboardValidator.class)
public @interface DashboardValidation {
    String message() default "invalid dashboard";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
