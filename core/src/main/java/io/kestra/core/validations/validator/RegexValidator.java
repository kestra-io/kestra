package io.kestra.core.validations.validator;

import io.kestra.core.validations.Regex;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Singleton
@Introspected
public class RegexValidator implements ConstraintValidator<Regex, String> {
    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<Regex> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            Pattern.compile(value);
        } catch (PatternSyntaxException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("invalid pattern [" + value + "]")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
