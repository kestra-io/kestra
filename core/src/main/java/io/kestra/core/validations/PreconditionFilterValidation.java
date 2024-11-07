package io.kestra.core.validations;

import io.kestra.core.validations.validator.PreconditionFilterValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PreconditionFilterValidator.class)
public @interface PreconditionFilterValidation {
    String message() default "invalid precondition filter";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
