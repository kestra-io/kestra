package io.kestra.core.validations;

import io.kestra.core.validations.validator.DateFormatValidator;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateFormatValidator.class)
public @interface DateFormat {
    String message() default "invalid date format value";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
