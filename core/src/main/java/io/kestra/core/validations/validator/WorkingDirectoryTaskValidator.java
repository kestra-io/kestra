package io.kestra.core.validations.validator;

import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.kestra.core.validations.WorkingDirectoryTaskValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class WorkingDirectoryTaskValidator  implements ConstraintValidator<WorkingDirectoryTaskValidation, WorkingDirectory> {
    @Override
    public boolean isValid(
        @Nullable WorkingDirectory value,
        @NonNull AnnotationValue<WorkingDirectoryTaskValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getTasks() == null || value.getTasks().isEmpty()) {
            context.messageTemplate("The 'tasks' property cannot be empty");
            return false;
        }

        if (value.getTasks().stream().anyMatch(task -> !(task instanceof RunnableTask<?>))) {
            context.messageTemplate("Only runnable tasks are allowed as children of a WorkingDirectory task");
            return false;
        }

        if (value.getTasks().stream().anyMatch(task -> task.getWorkerGroup() != null)) {
            context.messageTemplate("Cannot set a Worker Group in any WorkingDirectory sub-tasks, it is only supported at the WorkingDirectory level");
            return false;
        }

        return true;
    }
}
