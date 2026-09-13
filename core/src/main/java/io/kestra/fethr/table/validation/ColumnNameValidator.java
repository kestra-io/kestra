package io.kestra.fethr.table.validation;

import io.kestra.fethr.table.SqlIdentifierPatterns;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/** Validates a column name as a safe SQL identifier, rejecting the reserved primary-key column. */
@Singleton
@Introspected
public class ColumnNameValidator implements ConstraintValidator<ColumnNameCheck, String> {

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<ColumnNameCheck> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value != null
            && !value.equals(SqlIdentifierPatterns.PRIMARY_KEY)
            && SqlIdentifierPatterns.COLUMN_IDENTIFIER.matcher(value).matches();
    }
}
