package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.ExecutionsDataFilterKPIValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExecutionsDataFilterKPIValidator.class)
public @interface ExecutionsDataFilterKPIValidation {
    String message() default "invalid executions data filter";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
