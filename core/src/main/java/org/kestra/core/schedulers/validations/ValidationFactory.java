package org.kestra.core.schedulers.validations;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;

import javax.inject.Singleton;

@Factory
public class ValidationFactory {
    private static final CronParser CRON_PARSER = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

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
}

