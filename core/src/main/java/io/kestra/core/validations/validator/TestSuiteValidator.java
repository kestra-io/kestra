package io.kestra.core.validations.validator;

import io.kestra.core.services.FlowService;
import io.kestra.core.test.TestSuite;
import io.kestra.core.validations.TestSuiteValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@Introspected
public class TestSuiteValidator implements ConstraintValidator<TestSuiteValidation, TestSuite> {
    @Inject
    private FlowService flowService;

    @Override
    public boolean isValid(
        @Nullable TestSuite value,
        @NonNull AnnotationValue<TestSuiteValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (flowService.findById(value.getTenantId(), value.getNamespace(), value.getFlowId()).isEmpty()) {
            violations.add("Flow with id: '%s' does not exist on Namespace: '%s'.".formatted(value.getFlowId(), value.getNamespace()));
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid TestSuite: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }
}
