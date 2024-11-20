package io.kestra.core.validations.validator;

import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.validations.TimeWindowValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class TimeWindowValidator implements ConstraintValidator<TimeWindowValidation, TimeWindow> {

    @Override
    public boolean isValid(
        @Nullable TimeWindow value,
        @NonNull AnnotationValue<TimeWindowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return switch (value.getType()) {
            case DAILY_TIME_DEADLINE -> {
                if (value.getWindow() != null || value.getWindowAdvance() != null  || value.getStartTime() != null || value.getEndTime() != null) {
                    context.disableDefaultConstraintViolation();
                    if (value.getWindow() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_DEADLINE` cannot have a window.").addConstraintViolation();
                    }
                    if (value.getWindowAdvance() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_DEADLINE` cannot have a window advance.").addConstraintViolation();
                    }
                    if (value.getStartTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_DEADLINE` cannot have a start time.").addConstraintViolation();
                    }
                    if (value.getEndTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_DEADLINE` cannot have an end time.").addConstraintViolation();
                    }
                    yield false;
                }
                if (value.getDeadline() == null) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_DEADLINE` must have a deadline.").addConstraintViolation();
                    yield false;
                }
                yield true;
            }
            case DAILY_TIME_WINDOW -> {
                if (value.getWindow() != null || value.getWindowAdvance() != null  || value.getDeadline() != null) {
                    context.disableDefaultConstraintViolation();
                    if (value.getWindow() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_WINDOW` cannot have a window.").addConstraintViolation();
                    }
                    if (value.getWindowAdvance() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_WINDOW` cannot have a window advance.").addConstraintViolation();
                    }
                    if (value.getStartTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_WINDOW` cannot have a deadline.").addConstraintViolation();
                    }
                    yield false;
                }
                if (value.getStartTime() == null || value.getEndTime() == null) {
                    context.disableDefaultConstraintViolation();
                    if (value.getStartTime() == null ) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_WINDOW` must have a start time.").addConstraintViolation();
                    }
                    if (value.getEndTime() == null ) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DAILY_TIME_WINDOW` must have an end time.").addConstraintViolation();
                    }
                    yield false;
                }
                yield true;
            }
            case DURATION_WINDOW -> {
                if (value.getDeadline() != null || value.getStartTime() != null || value.getEndTime() != null) {
                    context.disableDefaultConstraintViolation();
                    if (value.getDeadline() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DURATION_WINDOW` cannot have a deadline.").addConstraintViolation();
                    }
                    if (value.getStartTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DURATION_WINDOW` cannot have a start time.").addConstraintViolation();
                    }
                    if (value.getEndTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `DURATION_WINDOW` cannot have an end time.").addConstraintViolation();
                    }
                    yield false;
                }
                yield true;
            }
            case SLIDING_WINDOW -> {
                if (value.getDeadline() != null || value.getStartTime() != null || value.getEndTime() != null || value.getWindowAdvance() != null) {
                    context.disableDefaultConstraintViolation();
                    if (value.getDeadline() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `SLIDING_WINDOW` cannot have a deadline.").addConstraintViolation();
                    }
                    if (value.getStartTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `SLIDING_WINDOW` cannot have a start time.").addConstraintViolation();
                    }
                    if (value.getEndTime() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `SLIDING_WINDOW` cannot have an end time.").addConstraintViolation();
                    }
                    if (value.getWindowAdvance() != null) {
                        context.buildConstraintViolationWithTemplate( "Time window of type `SLIDING_WINDOW` cannot have a window advance.").addConstraintViolation();
                    }
                    yield false;
                }
                yield true;
            }
        };
    }
}
