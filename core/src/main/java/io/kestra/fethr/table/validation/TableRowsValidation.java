package io.kestra.fethr.table.validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Checks that a row task carries the fields its chosen action needs.
 *
 * <p>
 * A class-level constraint because the answer depends on {@code action}: which other fields are
 * mandatory is only knowable once it is known.
 */
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TableRowsValidator.class)
public @interface TableRowsValidation {
    String message() default "invalid Rows task";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
