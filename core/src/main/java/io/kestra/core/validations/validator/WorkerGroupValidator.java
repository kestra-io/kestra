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
    // We previously use a different validator for EE,
    // but it is no longer possible due to https://github.com/micronaut-projects/micronaut-validation/issues/258.
    // So we check that the EE package exists.
    private static final Package EE_PACKAGE = ClassLoader.getSystemClassLoader().getDefinedPackage("io.kestra.ee.validation");

    @Override
    public boolean isValid(
        @Nullable WorkerGroup value,
        @NonNull AnnotationValue<WorkerGroupValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null|| value.getKey() == null) {
            return true;
        }

        if (EE_PACKAGE == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Worker Group is an Enterprise Edition functionality")
                .addConstraintViolation();
            return false;
        }
        return true;
    }
}
