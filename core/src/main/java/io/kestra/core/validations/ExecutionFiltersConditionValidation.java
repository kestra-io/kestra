package io.kestra.core.validations;

import io.kestra.core.validations.validator.ExecutionFiltersConditionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExecutionFiltersConditionValidator.class)
public @interface ExecutionFiltersConditionValidation {
    String message() default "invalid execution filters condition";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
