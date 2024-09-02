package io.kestra.core.validations.validator;

import io.kestra.core.models.property.Data;
import io.kestra.core.validations.DataValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
@SuppressWarnings("rawtypes")
public class DataValidator implements ConstraintValidator<DataValidation, Data> {
    @Override
    public boolean isValid(
        @Nullable Data value,
        @NonNull AnnotationValue<DataValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.isFromList() && value.isFromMap() ||
            value.isFromList() && value.isFromURI() ||
            value.isFromMap() && value.isFromURI()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate( "Only one of 'fromURI', 'fromMap' or 'fromList' can be set.")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}