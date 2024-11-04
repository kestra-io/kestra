package io.kestra.core.validations;

import io.kestra.core.validations.validator.SLAValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SLAValidator.class)
public @interface SLAValidation {
    String message() default "invalid SLA definition";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
