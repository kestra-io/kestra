package io.kestra.core.validations.validator;

import io.kestra.core.models.conditions.types.MultipleCondition;
import io.kestra.core.models.triggers.types.Webhook;
import io.kestra.core.validations.WebhookValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class WebhookValidator implements ConstraintValidator<WebhookValidation, Webhook> {
    @Override
    public boolean isValid(
        @Nullable Webhook value,
        @NonNull AnnotationValue<WebhookValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getConditions() != null) {
            if (value.getConditions().stream().anyMatch(condition -> condition instanceof MultipleCondition)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("invalid webhook: conditions of type MultipleCondition are not supported")
                    .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
