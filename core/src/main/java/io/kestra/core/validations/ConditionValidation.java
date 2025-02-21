package io.kestra.core.validations;

import io.kestra.core.validations.validator.ConditionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConditionValidator.class)
public @interface ConditionValidation {
    String message() default "one condition must be set";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
