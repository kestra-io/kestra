package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.WorkingDirectoryTaskValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WorkingDirectoryTaskValidator.class)
public @interface WorkingDirectoryTaskValidation {
    String message() default "invalid WorkingDirectory task";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
