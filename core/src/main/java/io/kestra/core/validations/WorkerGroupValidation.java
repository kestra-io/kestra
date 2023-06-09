package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { })
public @interface WorkerGroupValidation {
    String message() default "invalid workerGroup property";
}
