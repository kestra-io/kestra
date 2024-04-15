package io.kestra.core.validations;

import io.kestra.core.validations.validator.WorkingDirectoryTaskValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WorkingDirectoryTaskValidator.class)
public @interface WorkingDirectoryTaskValidation {
    String message() default "invalid WorkingDirectory task";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
