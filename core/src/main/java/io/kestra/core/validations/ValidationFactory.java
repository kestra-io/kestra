package io.kestra.core.validations;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jakarta.inject.Singleton;

@Factory
public class ValidationFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Singleton
    ConstraintValidator<DateFormat, String> dateTimeValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true; // nulls are allowed according to spec
            }

            try {
                Date now = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat(value);
                dateFormat.format(now);
            } catch (Exception e) {
                context.messageTemplate("invalid date format value '({validatedValue})': " + e.getMessage());

                return false;
            }
            return true;
        };
    }

    @Singleton
    ConstraintValidator<CronExpression, CharSequence> cronExpressionValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                Cron parse = io.kestra.core.models.triggers.types.Schedule.CRON_PARSER.parse(value.toString());
                parse.validate();
            } catch (IllegalArgumentException e) {
                context.messageTemplate("invalid cron expression '({validatedValue})': " + e.getMessage());

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<Schedule, io.kestra.core.models.triggers.types.Schedule> scheduleValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            if (value.getBackfill() != null && value.getBackfill().getStart() != null && value.getLateMaximumDelay() != null) {
                context.messageTemplate("invalid schedule: backfill and lateMaximumDelay are incompatible options");

                return false;
            }

            return true;
        };
    }

    @Singleton
    ConstraintValidator<JsonString, String> jsonStringValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                OBJECT_MAPPER.readTree(value);
            } catch (IOException e) {
                context.messageTemplate("invalid json '({validatedValue})': " + e.getMessage());

                return false;
            }
            return true;
        };
    }
}

