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
import javax.inject.Singleton;

@Factory
public class ValidationFactory {
    private static final CronParser CRON_PARSER = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
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
                Cron parse = CRON_PARSER.parse(value.toString());
                parse.validate();
            } catch (IllegalArgumentException e) {
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
                return false;
            }
            return true;
        };
    }
}

