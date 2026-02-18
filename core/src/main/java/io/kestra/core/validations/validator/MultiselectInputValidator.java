package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.validations.MultiselectInputValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class MultiselectInputValidator implements ConstraintValidator<MultiselectInputValidation, MultiselectInput> {
    @Override
    public boolean isValid(@Nullable MultiselectInput value, @NonNull AnnotationValue<MultiselectInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (value.getItemType() == Type.ARRAY
            || value.getItemType() == Type.SECRET
            || value.getItemType() == Type.MULTISELECT
            || value.getItemType() == Type.ENUM
            || value.getItemType() == Type.SELECT
        ) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("`itemType` cannot be "+ value.getItemType())
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
