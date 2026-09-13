package io.kestra.fethr.table.validation;

import io.kestra.fethr.table.ColumnDataType;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/** Requires a primary-key-eligible type, which {@link ColumnDataType} itself decides. */
@Singleton
@Introspected
public class PrimaryKeyTypeValidator implements ConstraintValidator<PrimaryKeyType, ColumnDataType> {

    @Override
    public boolean isValid(
        @Nullable ColumnDataType value,
        @NonNull AnnotationValue<PrimaryKeyType> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value != null && value.isPrimaryKey();
    }
}
