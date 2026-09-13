package io.kestra.fethr.table.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

/**
 * A user-defined table name: valid under the configured {@link TableNameValidationStrategy} and not
 * reserved.
 *
 * <p>
 * Checked on the form so an invalid name is refused before any DDL runs, rather than adjusted after.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TableNameValidator.class)
@Target({ FIELD, PARAMETER, TYPE_USE })
public @interface TableName {
    String message() default "must be a valid table name (a lowercase letter followed by lowercase letters, digits or underscores) and not a reserved name";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
