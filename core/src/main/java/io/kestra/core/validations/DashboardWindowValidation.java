package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.DashboardWindowValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DashboardWindowValidator.class)
public @interface DashboardWindowValidation {
    String message() default "invalid time window definition";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
