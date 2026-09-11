package io.kestra.core.validations.validator;

import io.kestra.core.validations.BatchWebhookValidation;
import io.kestra.plugin.core.trigger.BatchWebhook;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class BatchWebhookValidator implements ConstraintValidator<BatchWebhookValidation, BatchWebhook> {
    @Override
    public boolean isValid(
        @Nullable BatchWebhook value,
        @NonNull AnnotationValue<BatchWebhookValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getEventsCount() == null && value.getPollingInterval() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "invalid batch webhook: at least one of 'eventsCount' or 'pollingInterval' must be set"
            ).addConstraintViolation();
            return false;
        }

        if (value.getEventsCount() != null && value.getEventsCount() < 1) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "invalid batch webhook: 'eventsCount' must be greater than or equal to 1"
            ).addConstraintViolation();
            return false;
        }

        if (value.getPollingInterval() != null
            && (value.getPollingInterval().isNegative() || value.getPollingInterval().isZero())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "invalid batch webhook: 'pollingInterval' must be strictly positive"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
