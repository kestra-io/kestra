package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.FlowService;
import io.kestra.core.test.TestSuite;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.validations.TestSuiteValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
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

        Optional<Flow> maybeFlow = flowService.findById(value.getTenantId(), value.getNamespace(), value.getFlowId());
        if (maybeFlow.isEmpty()) {
            violations.add("Flow with id: '%s' does not exist on Namespace: '%s'.".formatted(value.getFlowId(), value.getNamespace()));
        } else {
            Flow flow = maybeFlow.get();
            if (!ListUtils.isEmpty(value.getTestCases())) {
                value.getTestCases().forEach(it -> validate(flow, it, violations));
            }
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

    private void validate(Flow flow, UnitTest unitTest, List<String> violations) {
        if (unitTest == null || unitTest.getFixtures() == null) {
            return;
        }

        if (!MapUtils.isEmpty(unitTest.getFixtures().getInputs())) {
            unitTest.getFixtures().getInputs().forEach((key, _) -> validateInput(flow, key, violations));
        }

        if (!ListUtils.isEmpty(unitTest.getFixtures().getTasks())) {
            unitTest.getFixtures().getTasks().forEach(it -> validateTask(flow, it, violations));
        }
    }

    private void validateTask(Flow flow, TaskFixture taskFixture, List<String> violations) {
        if (flow.allTasks().noneMatch(it -> it.getId().equals(taskFixture.getId()))) {
            violations.add("Task '" + taskFixture.getId() + "' does not exist in the flow");
        }
    }

    private void validateInput(Flow flow, String inputs, List<String> violations) {
        if (ListUtils.emptyOnNull(flow.getInputs()).stream().noneMatch(it -> it.getId().equals(inputs))) {
            violations.add("Input '" + inputs + "' does not exist in the flow");
        }
    }
}
