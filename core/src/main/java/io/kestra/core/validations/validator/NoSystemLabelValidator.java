package io.kestra.core.validations.validator;

import io.kestra.core.models.Label;
import io.kestra.core.validations.NoSystemLabelValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class NoSystemLabelValidator implements ConstraintValidator<NoSystemLabelValidation, Label> {
    @Override
    public boolean isValid(@Nullable Label value, @NonNull AnnotationValue<NoSystemLabelValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (value.key() != null && value.key().startsWith(Label.SYSTEM_PREFIX)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("System labels can only be set by Kestra itself, offending label: " + value.key() + "=" + value.value() + ".")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
