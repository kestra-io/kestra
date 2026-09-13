package io.kestra.fethr.table.validation;

import io.kestra.fethr.table.ColumnDataType;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/** Requires a column data type to be present, an unknown name having deserialized to null. */
@Singleton
@Introspected
public class ColumnDataTypeValidator implements ConstraintValidator<ColumnDataTypeCheck, ColumnDataType> {

    @Override
    public boolean isValid(
        @Nullable ColumnDataType value,
        @NonNull AnnotationValue<ColumnDataTypeCheck> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value != null;
    }
}
