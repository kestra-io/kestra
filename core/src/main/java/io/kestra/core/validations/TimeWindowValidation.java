package io.kestra.core.validations;

import io.kestra.core.validations.validator.TimeWindowValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeWindowValidator.class)
public @interface TimeWindowValidation {
    String message() default "invalid time window definition";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
