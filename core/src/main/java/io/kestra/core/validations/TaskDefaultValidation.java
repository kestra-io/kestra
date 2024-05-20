package io.kestra.core.validations;

import io.kestra.core.validations.validator.TaskDefaultValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TaskDefaultValidator.class)
public @interface TaskDefaultValidation {
    String message() default "invalid taskDefault";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
