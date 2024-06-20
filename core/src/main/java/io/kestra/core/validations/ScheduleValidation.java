package io.kestra.core.validations;

import io.kestra.core.validations.validator.ScheduleValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ScheduleValidator.class)
public @interface ScheduleValidation {
    String message() default "invalid cron expression ({validatedValue.cron})";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}