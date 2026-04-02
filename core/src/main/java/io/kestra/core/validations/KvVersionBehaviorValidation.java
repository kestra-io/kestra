package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.KvVersionBehaviorValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KvVersionBehaviorValidator.class)
public @interface KvVersionBehaviorValidation {
    String message() default "invalid `version` behavior configuration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
