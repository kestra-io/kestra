package io.kestra.core.validations;

import io.kestra.core.validations.validator.SwitchTaskValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SwitchTaskValidator.class)
public @interface SwitchTaskValidation {
    String message() default "invalid Switch task";
}
