package io.kestra.core.validations;

import io.kestra.core.validations.validator.UrlValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Validates the annotated string is a URL.
 * Optionally, enforces the URL scheme as regex.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UrlValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface Url {
    String message() default "invalid URL [{validatedValue}]";
    /**
     * @return the URL scheme pattern, e.g. <code>(http|https)</code>. Defaults to <code>.*</code>.
     */
    String scheme() default ".*";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
