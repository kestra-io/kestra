package io.kestra.core.validations;

import javax.validation.Constraint;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { })
public @interface Regex {
    String message() default "invalid pattern ({validatedValue})";
}
