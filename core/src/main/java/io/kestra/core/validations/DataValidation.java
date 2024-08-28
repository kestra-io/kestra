package io.kestra.core.validations;

import io.kestra.core.validations.validator.DataValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataValidator.class)
public @interface DataValidation {
    String message() default "invalid data property";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
