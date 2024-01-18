package io.kestra.core.validations.validator;

import io.kestra.core.tasks.flows.Dag;
import io.kestra.core.validations.DagTaskValidation;
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
public class DagTaskValidator  implements ConstraintValidator<DagTaskValidation, Dag> {
    @Override
    public boolean isValid(
        @Nullable Dag value,
        @NonNull AnnotationValue<DagTaskValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getTasks() == null || value.getTasks().isEmpty()) {
            context.messageTemplate("No task defined");

            return false;
        }

        List<Dag.DagTask> taskDepends = value.getTasks();

        // Check for not existing taskId
        List<String> invalidDependencyIds = value.dagCheckNotExistTask(taskDepends);
        if (!invalidDependencyIds.isEmpty()) {
            String errorMessage = "Not existing task id in dependency: " + String.join(", ", invalidDependencyIds);
            context.messageTemplate(errorMessage);

            return false;
        }

        // Check for cyclic dependencies
        ArrayList<String> cyclicDependency = value.dagCheckCyclicDependencies(taskDepends);
        if (!cyclicDependency.isEmpty()) {
            context.messageTemplate("Cyclic dependency detected: " + String.join(", ", cyclicDependency));

            return false;
        }

        return true;
    }
}
