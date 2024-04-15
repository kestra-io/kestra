package io.kestra.core.validations;

import io.kestra.core.validations.validator.SwitchTaskValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SwitchTaskValidator.class)
public @interface SwitchTaskValidation {
    String message() default "invalid Switch task";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
