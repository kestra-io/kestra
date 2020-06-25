package org.kestra.core.schedulers.validations;

import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Predictor;

import javax.inject.Singleton;

@Factory
public class ValidationFactory {
    @Singleton
    ConstraintValidator<CronExpression, CharSequence> cronExpressionValidator() {
        return (value, annotationMetadata, context) -> {
            if (value == null) {
                return true;
            }

            try {
                new Predictor(value.toString()).nextMatchingDate();
            } catch (InvalidPatternException e) {
                return false;
            }

            return true;
        };
    }
}

