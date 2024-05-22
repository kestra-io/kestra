package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.ArrayInput;
import io.kestra.core.validations.ArrayInputValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class ArrayInputValidator implements ConstraintValidator<ArrayInputValidation, ArrayInput> {
    @Override
    public boolean isValid(@Nullable ArrayInput value, @NonNull AnnotationValue<ArrayInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (value.getItemType() == Type.ARRAY) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`itemType` cannot be `ARRAY`")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
