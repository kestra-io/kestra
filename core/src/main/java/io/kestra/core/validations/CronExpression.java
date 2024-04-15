package io.kestra.core.validations;

import io.kestra.core.validations.validator.CronExpressionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CronExpressionValidator.class)
public @interface CronExpression {
    String message() default "invalid cron expression ({validatedValue})";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}