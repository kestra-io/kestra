package io.kestra.core.validations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.kestra.core.validations.validator.TestSuiteAssertionValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TestSuiteAssertionValidator.class)
public @interface TestSuiteAssertionValidation {
    String message() default "invalid TestSuite Assertion";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
