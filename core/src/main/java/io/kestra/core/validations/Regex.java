package io.kestra.core.validations;

import io.kestra.core.validations.validator.RegexValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegexValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface Regex {
    String message() default "invalid pattern ({validatedValue})";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
