package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.validations.FlowValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@Introspected
public class FlowValidator  implements ConstraintValidator<FlowValidation, Flow> {
    @Override
    public boolean isValid(
        @Nullable Flow value,
        @NonNull AnnotationValue<FlowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        // tasks unique id
        List<String> taskIds = value.allTasksWithChilds()
            .stream()
            .map(Task::getId)
            .toList();

        List<String> duplicateIds = getDuplicates(taskIds);

        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate task id with name [" + String.join(", ", duplicateIds) + "]");
        }

        duplicateIds = getDuplicates(value.allTriggerIds());

        if (!duplicateIds.isEmpty()) {
            violations.add("Duplicate trigger id with name [" + String.join(", ", duplicateIds) + "]");
        }

        value.allTasksWithChilds()
            .forEach(
                task -> {
                    if (task instanceof ExecutableTask<?> executableTask
                        && value.getId().equals(executableTask.subflowId().flowId())
                        && value.getNamespace().equals(executableTask.subflowId().namespace())) {
                        violations.add("Recursive call to flow [" + value.getNamespace() + "." + value.getId() + "]");
                    }
                }
            );

        // input unique name
        if (value.getInputs() != null) {
            List<String> duplicates = getDuplicates(value.getInputs().stream().map(Data::getId).toList());
            if (!duplicates.isEmpty()) {
                violations.add("Duplicate input with name [" + String.join(", ", duplicates) + "]");
            }
        }
        // output unique name
        if (value.getOutputs() != null) {
            List<String> duplicates = getDuplicates(value.getOutputs().stream().map(Data::getId).toList());
            if (!duplicates.isEmpty()) {
                violations.add("Duplicate output with name [" + String.join(", ", duplicates) + "]");
            }
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid Flow: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }

    private static List<String> getDuplicates(List<String> taskIds) {
        return taskIds.stream()
            .distinct()
            .filter(entry -> Collections.frequency(taskIds, entry) > 1)
            .toList();
    }
}
