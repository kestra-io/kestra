package io.kestra.core.validations;

import io.kestra.core.validations.validator.DagTaskValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DagTaskValidator.class)
public @interface DagTaskValidation {
    String message() default "invalid Dag task";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
