package io.kestra.core.validations.validator;

import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.validations.WorkingDirectoryTaskValidation;
import io.kestra.plugin.core.flow.WorkingDirectory;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class WorkingDirectoryTaskValidator implements ConstraintValidator<WorkingDirectoryTaskValidation, WorkingDirectory> {
    @Override
    public boolean isValid(
        @Nullable WorkingDirectory value,
        @NonNull AnnotationValue<WorkingDirectoryTaskValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getTasks() == null || value.getTasks().isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("The 'tasks' property cannot be empty")
                .addConstraintViolation();
            return false;
        }

        if (value.allChildTasks().stream().anyMatch(WorkingDirectoryTaskValidator::hasWorkerSelector)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Cannot set routing requirements (workerSelector) on WorkingDirectory sub-tasks, only the WorkingDirectory itself can carry them")
                .addConstraintViolation();
            return false;
        }

        return true;
    }

    private static boolean hasWorkerSelector(Task task) {
        return task.getWorkerSelector() != null
            || task instanceof FlowableTask<?> flowable && flowable.allChildTasks().stream().anyMatch(WorkingDirectoryTaskValidator::hasWorkerSelector);
    }
}
