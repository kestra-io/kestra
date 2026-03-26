package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.TestSuiteValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TestSuiteValidator.class)
public @interface TestSuiteValidation {
    String message() default "invalid TestSuite";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
