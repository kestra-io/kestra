package io.kestra.core.validations;

import io.kestra.core.validations.validator.ScheduleValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.validation.Constraint;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ScheduleValidator.class)
public @interface Schedule {
    String message() default "invalid schedule ({validatedValue})";
}