package io.kestra.core.validations.validator;

import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.validations.WorkerGroupValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class WorkerGroupValidator  implements ConstraintValidator<WorkerGroupValidation, WorkerGroup> {
    @Override
    public boolean isValid(
        @Nullable WorkerGroup value,
        @NonNull AnnotationValue<WorkerGroupValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        context.messageTemplate("Worker Group is an Enterprise Edition functionality");
        return false;
    }
}
