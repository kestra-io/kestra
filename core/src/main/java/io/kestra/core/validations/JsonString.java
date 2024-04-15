package io.kestra.core.validations;

import io.kestra.core.validations.validator.JsonStringValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JsonStringValidator.class)
public @interface JsonString {
    String message() default "invalid json ({validatedValue})";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}