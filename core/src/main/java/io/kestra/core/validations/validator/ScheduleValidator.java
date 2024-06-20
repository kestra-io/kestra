package io.kestra.core.validations.validator;

import com.cronutils.model.Cron;
import io.kestra.core.validations.ScheduleValidation;
import io.kestra.plugin.core.trigger.Schedule;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class ScheduleValidator implements ConstraintValidator<ScheduleValidation, Schedule> {
    @Override
    public boolean isValid(
        @Nullable Schedule value,
        @NonNull AnnotationValue<ScheduleValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getCron() != null) { // if null, the standard @NotNull will do its job
            try {
                Cron parsed = value.parseCron();
                parsed.validate();
            } catch (IllegalArgumentException e) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate( "invalid cron expression '" + value.getCron() + "': " + e.getMessage())
                    .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
