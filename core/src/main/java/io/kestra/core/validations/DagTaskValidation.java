package io.kestra.core.validations;

import io.kestra.core.validations.validator.DagTaskValidator;
import javax.validation.Constraint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DagTaskValidator.class)
public @interface DagTaskValidation {
    String message() default "invalid Dag task";
}
