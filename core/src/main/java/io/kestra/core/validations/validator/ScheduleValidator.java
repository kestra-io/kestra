package io.kestra.core.validations.validator;

import io.kestra.core.validations.Schedule;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class ScheduleValidator  implements ConstraintValidator<Schedule, io.kestra.core.models.triggers.types.Schedule> {
    @Override
    public boolean isValid(
        @Nullable io.kestra.core.models.triggers.types.Schedule value,
        @NonNull AnnotationValue<Schedule> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getBackfill() != null && value.getBackfill().getStart() != null && value.getLateMaximumDelay() != null) {
            context.messageTemplate("invalid schedule: backfill and lateMaximumDelay are incompatible options");

            return false;
        }

        return true;
    }
}
