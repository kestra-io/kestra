package io.kestra.core.validations;

import io.kestra.core.validations.validator.TimezoneIdValidator;

import javax.validation.Constraint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimezoneIdValidator.class)
public @interface TimezoneId {
    String message() default "invalid timezone ({validatedValue})";
}