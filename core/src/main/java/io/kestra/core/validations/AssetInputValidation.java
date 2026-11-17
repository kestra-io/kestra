package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.AssetInputValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AssetInputValidator.class)
public @interface AssetInputValidation {
    String message() default "invalid asset input";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
