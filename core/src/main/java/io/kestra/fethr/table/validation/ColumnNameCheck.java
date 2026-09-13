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
 * A user-defined column name: a safe SQL identifier, and not the reserved primary-key column.
 *
 * <p>
 * The name goes into column DDL, so it is refused on the form before any ALTER runs. Member-less on
 * purpose, like {@link TableName} -- see {@link PrimaryKeyType} for why that matters.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ColumnNameValidator.class)
@Target({ FIELD, PARAMETER, TYPE_USE })
public @interface ColumnNameCheck {
    String message() default "must be a valid column name (a letter or underscore followed by letters, digits or underscores)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
