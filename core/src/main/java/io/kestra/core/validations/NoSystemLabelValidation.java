package io.kestra.core.validations;

import io.kestra.core.validations.validator.NoSystemLabelValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoSystemLabelValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.TYPE_USE})
public @interface NoSystemLabelValidation {
    String message() default "System labels can only be set by Kestra itself.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
