package io.kestra.core.validations;

import io.kestra.core.validations.validator.FlowValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FlowValidator.class)
public @interface FlowValidation {
    String message() default "invalid Flow";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
