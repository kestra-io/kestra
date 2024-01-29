package io.kestra.core.validations;

import io.kestra.core.validations.validator.RegexValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegexValidator.class)
public @interface Regex {
    String message() default "invalid pattern ({validatedValue})";
}
