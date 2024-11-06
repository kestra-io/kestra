package io.kestra.core.validations;

import io.kestra.core.validations.validator.TimeSLAValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeSLAValidator.class)
public @interface TimeSLAValidation {
    String message() default "invalid SLA definition";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
