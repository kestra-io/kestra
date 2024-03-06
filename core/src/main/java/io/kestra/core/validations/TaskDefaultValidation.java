package io.kestra.core.validations;

import jakarta.validation.Constraint;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
public @interface TaskDefaultValidation {
    String message() default "invalid taskDefault";
}
