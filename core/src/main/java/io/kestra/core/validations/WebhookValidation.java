package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.kestra.core.validations.validator.WebhookValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { WebhookValidator.class })
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
public @interface WebhookValidation {
    String message() default "invalid webhook ({validatedValue})";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
