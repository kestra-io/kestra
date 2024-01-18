package io.kestra.core.validations.validator;

import com.cronutils.model.Cron;
import io.kestra.core.validations.CronExpression;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class CronExpressionValidator implements ConstraintValidator<CronExpression, String> {
    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<CronExpression> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            Cron parse = io.kestra.core.models.triggers.types.Schedule.CRON_PARSER.parse(value);
            parse.validate();
        } catch (IllegalArgumentException e) {
            context.messageTemplate("invalid cron expression '({validatedValue})': " + e.getMessage());

            return false;
        }

        return true;
    }
}
