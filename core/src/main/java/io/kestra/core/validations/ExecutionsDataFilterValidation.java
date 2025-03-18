package io.kestra.core.validations;

import io.kestra.core.validations.validator.ExecutionsDataFilterValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExecutionsDataFilterValidator.class)
public @interface ExecutionsDataFilterValidation {
    String message() default "invalid executions data filter";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
