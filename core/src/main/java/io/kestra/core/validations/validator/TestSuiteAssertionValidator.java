package io.kestra.core.validations.validator;

import io.kestra.core.test.flow.Assertion;
import io.kestra.core.validations.TestSuiteAssertionValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@Introspected
public class TestSuiteAssertionValidator implements ConstraintValidator<TestSuiteAssertionValidation, Assertion> {

    @Override
    public boolean isValid(
        @Nullable Assertion value,
        @NonNull AnnotationValue<TestSuiteAssertionValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (!value.hasAtLeastOneAssertion()) {
            violations.add("at least one actual assertion is required (equalTo, greaterThan..)");
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid TestSuite Assertion: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }
}
