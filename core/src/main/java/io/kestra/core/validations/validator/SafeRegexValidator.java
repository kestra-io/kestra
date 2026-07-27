package io.kestra.core.validations.validator;

import io.kestra.core.utils.RegexUtils;
import io.kestra.core.validations.SafeRegexValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class SafeRegexValidator implements ConstraintValidator<SafeRegexValidation, String> {
    @Override
    public boolean isValid(@Nullable String value, @NonNull AnnotationValue<SafeRegexValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        return value == null || RegexUtils.isSafeUserRegex(value);
    }
}
