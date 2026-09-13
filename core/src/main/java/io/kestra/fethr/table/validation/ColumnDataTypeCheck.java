package io.kestra.fethr.table.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

/**
 * A column data type that is present.
 *
 * <p>
 * The annotated field reads an unknown enum name as null, so this rejects both a missing type and an
 * unrecognised one -- and does it as a validation failure rather than a Jackson error, which is what
 * turns it into a clean 422. Primary-key eligibility is a separate question; see
 * {@link PrimaryKeyType}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ColumnDataTypeValidator.class)
@Target({ FIELD, PARAMETER, TYPE_USE, ANNOTATION_TYPE })
public @interface ColumnDataTypeCheck {
    String message() default "must be a valid column data type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
