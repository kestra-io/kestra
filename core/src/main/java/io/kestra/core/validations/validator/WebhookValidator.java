package io.kestra.core.validations.validator;

import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.core.validations.WebhookValidation;
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
public class WebhookValidator implements ConstraintValidator<WebhookValidation, Webhook> {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        MediaType.APPLICATION_JSON,
        MediaType.TEXT_PLAIN
    );

    @Override
    public boolean isValid(
        @Nullable Webhook value,
        @NonNull AnnotationValue<WebhookValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getResponseContentType() != null && !ALLOWED_CONTENT_TYPES.contains(value.getResponseContentType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("invalid webhook: responseContentType must be either 'application/json' or 'text/plain'")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
