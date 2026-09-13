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
 * A column data type that is present and can back a primary key.
 *
 * <p>
 * A separate constraint rather than a flag on {@link ColumnDataTypeCheck}, and that is not a style
 * choice: Micronaut does not carry constraint annotation members into the enforced validation pass
 * under {@code @ExecuteOn} with cascaded body validation, so a flag-driven branch never runs at all.
 * A member-less constraint does enforce.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PrimaryKeyTypeValidator.class)
@Target({ FIELD, PARAMETER, TYPE_USE })
public @interface PrimaryKeyType {
    String message() default "must be a primary-key-eligible column data type (UUID or NUMBER)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
