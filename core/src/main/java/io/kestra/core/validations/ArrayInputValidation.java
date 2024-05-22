package io.kestra.core.validations;

import io.kestra.core.validations.validator.ArrayInputValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ArrayInputValidator.class)
public @interface ArrayInputValidation {
    String message() default "invalid array input";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
