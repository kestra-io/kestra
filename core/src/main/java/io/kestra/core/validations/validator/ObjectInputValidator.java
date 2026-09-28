package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.input.ObjectInput;
import io.kestra.core.validations.ObjectInputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class ObjectInputValidator implements ConstraintValidator<ObjectInputValidation, ObjectInput> {
    @Override
    public boolean isValid(@Nullable ObjectInput value, @NonNull AnnotationValue<ObjectInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return RecordFieldsValidator.report(RecordFieldsValidator.violations("properties", value.getProperties()), context);
    }
}
