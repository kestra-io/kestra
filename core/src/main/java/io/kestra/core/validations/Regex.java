package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.RegexValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.*;

/**
 * Validates that a string compiles as a regular expression.
 *
 * @deprecated use {@link SafeRegexValidation}, which also rejects patterns prone to catastrophic backtracking.
 */
@Deprecated(forRemoval = true, since = "2.1.0")
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegexValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface Regex {
    String message() default "invalid pattern ({validatedValue})";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
