package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.FlowTriggerValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FlowTriggerValidator.class)
public @interface FlowTriggerValidation {
    String message() default "invalid flow trigger configuration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
