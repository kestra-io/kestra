package io.kestra.core.validations.validator;

import io.kestra.core.validations.KvVersionBehaviorValidation;
import io.kestra.plugin.core.kv.Version;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class KvVersionBehaviorValidator implements ConstraintValidator<KvVersionBehaviorValidation, Version> {
    @Override
    public boolean isValid(
        @Nullable Version value,
        @NonNull AnnotationValue<KvVersionBehaviorValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.getBefore() != null && value.getKeepAmount() != null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Cannot set both 'before' and 'keepAmount' properties")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
