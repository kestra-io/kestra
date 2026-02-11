package io.kestra.core.validations.validator;

import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.validations.AbstractWebhookValidation;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.Set;

@Singleton
@Introspected
public class AbstractWebhookValidator implements ConstraintValidator<AbstractWebhookValidation, AbstractWebhookTrigger> {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_PLAIN
    );

    @Override
    public boolean isValid(
        @Nullable AbstractWebhookTrigger value,
        @NonNull AnnotationValue<AbstractWebhookValidation> annotationMetadata,
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
