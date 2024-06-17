package io.kestra.core.validations;

import io.kestra.core.validations.validator.FileInputValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileInputValidator.class)
public @interface FileInputValidation {
    String message() default "invalid file input";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
