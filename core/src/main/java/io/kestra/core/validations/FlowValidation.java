package io.kestra.core.validations;

import io.kestra.core.validations.validator.FlowValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FlowValidator.class)
public @interface FlowValidation {
    String message() default "invalid Flow";
}
