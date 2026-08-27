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
        if (value == null) {
            return true;
        }

        if (!RegexUtils.isSafeUserRegex(value)) {
            return false;
        }

        return RegexUtils.syntaxError(value)
            .map(error -> {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("invalid regex pattern '" + value + "': " + error)
                    .addConstraintViolation();
                return false;
            })
            .orElse(true);
    }
}
