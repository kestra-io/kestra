package org.kestra.core.schedulers.validations;

import javax.validation.Constraint;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { })
public @interface CronExpression {
    String message() default "invalid cron expression ({validatedValue})";
}