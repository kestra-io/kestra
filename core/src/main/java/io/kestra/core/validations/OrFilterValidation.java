package io.kestra.core.validations;

import io.kestra.core.validations.validator.OrFilterValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrFilterValidator.class)
public @interface OrFilterValidation {
    String message() default "invalid filter";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
