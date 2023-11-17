package io.kestra.core.validations;

import io.kestra.core.validations.validator.CronExpressionValidator;
import javax.validation.Constraint;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CronExpressionValidator.class)
public @interface CronExpression {
    String message() default "invalid cron expression ({validatedValue})";
}