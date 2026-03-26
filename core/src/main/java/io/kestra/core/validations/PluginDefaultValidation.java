package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.PluginDefaultValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PluginDefaultValidator.class)
public @interface PluginDefaultValidation {
    String message() default "invalid plugin default";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
