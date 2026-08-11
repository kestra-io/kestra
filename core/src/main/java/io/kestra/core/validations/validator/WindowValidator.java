package io.kestra.core.validations.validator;

import io.kestra.core.models.triggers.Window;
import io.kestra.core.validations.WindowValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class WindowValidator implements ConstraintValidator<WindowValidation, Window> {

    @Override
    public boolean isValid(
        @Nullable Window value,
        @NonNull AnnotationValue<WindowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getDeadline() != null) {
            return validateDailyTimeDeadline(value, context);
        }
        if (value.getFrom() != null && value.getTo() != null) {
            return validateDailyTimeWindow(value, context);
        }
        if (value.getFrom() != null || value.getTo() != null) {
            context.disableDefaultConstraintViolation();
            if (value.getFrom() == null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` must have a from time.").addConstraintViolation();
            } else {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` must have a to time.").addConstraintViolation();
            }
            return false;
        }
        if (value.getLookback() != null) {
            return validateSlidingWindow(value, context);
        }
        return true;
    }

    private boolean validateDailyTimeDeadline(Window value, ConstraintValidatorContext context) {
        if (value.getEvery() != null || value.getOffset() != null || value.getFrom() != null || value.getTo() != null || value.getLookback() != null) {
            context.disableDefaultConstraintViolation();
            if (value.getEvery() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_DEADLINE` cannot have an every duration.").addConstraintViolation();
            }
            if (value.getOffset() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_DEADLINE` cannot have an offset.").addConstraintViolation();
            }
            if (value.getFrom() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_DEADLINE` cannot have a from time.").addConstraintViolation();
            }
            if (value.getTo() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_DEADLINE` cannot have a to time.").addConstraintViolation();
            }
            if (value.getLookback() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_DEADLINE` cannot have a lookback duration.").addConstraintViolation();
            }
            return false;
        }
        return true;
    }

    private boolean validateDailyTimeWindow(Window value, ConstraintValidatorContext context) {
        if (value.getDeadline() != null || value.getEvery() != null || value.getOffset() != null || value.getLookback() != null) {
            context.disableDefaultConstraintViolation();
            if (value.getDeadline() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` cannot have a deadline.").addConstraintViolation();
            }
            if (value.getEvery() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` cannot have an every duration.").addConstraintViolation();
            }
            if (value.getOffset() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` cannot have an offset.").addConstraintViolation();
            }
            if (value.getLookback() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `DAILY_TIME_WINDOW` cannot have a lookback duration.").addConstraintViolation();
            }
            return false;
        }
        return true;
    }

    private boolean validateSlidingWindow(Window value, ConstraintValidatorContext context) {
        if (value.getDeadline() != null || value.getFrom() != null || value.getTo() != null || value.getOffset() != null || value.getEvery() != null) {
            context.disableDefaultConstraintViolation();
            if (value.getDeadline() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `SLIDING_WINDOW` cannot have a deadline.").addConstraintViolation();
            }
            if (value.getFrom() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `SLIDING_WINDOW` cannot have a from time.").addConstraintViolation();
            }
            if (value.getTo() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `SLIDING_WINDOW` cannot have a to time.").addConstraintViolation();
            }
            if (value.getOffset() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `SLIDING_WINDOW` cannot have an offset.").addConstraintViolation();
            }
            if (value.getEvery() != null) {
                context.buildConstraintViolationWithTemplate("Window of type `SLIDING_WINDOW` cannot have an every duration.").addConstraintViolation();
            }
            return false;
        }
        return true;
    }
}
