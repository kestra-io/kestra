package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.MultiselectInputValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultiselectInputValidator.class)
public @interface MultiselectInputValidation {
    String message() default "invalid multiselect input";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
