package io.kestra.core.validations;

import io.kestra.core.validations.validator.FilesVersionBehaviorValidator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FilesVersionBehaviorValidator.class)
public @interface FilesVersionBehaviorValidation {
    String message() default "invalid `version` behavior configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
