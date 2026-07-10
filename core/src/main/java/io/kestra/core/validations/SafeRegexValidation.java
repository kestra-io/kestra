package io.kestra.core.validations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.SafeRegexValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeRegexValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE })
public @interface SafeRegexValidation {
    String message() default "regex pattern is too long or prone to catastrophic backtracking";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
