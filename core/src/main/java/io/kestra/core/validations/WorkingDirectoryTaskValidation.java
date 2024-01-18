package io.kestra.core.validations;

import io.kestra.core.validations.validator.WorkingDirectoryTaskValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WorkingDirectoryTaskValidator.class)
public @interface WorkingDirectoryTaskValidation {
    String message() default "invalid WorkingDirectory task";
}
