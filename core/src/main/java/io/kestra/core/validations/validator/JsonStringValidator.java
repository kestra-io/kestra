package io.kestra.core.validations.validator;

import io.kestra.core.validations.JsonString;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class JsonStringValidator implements ConstraintValidator<JsonString, String> {
    // FAIL_ON_TRAILING_TOKENS defaults to enabled in Jackson 3 (was disabled in Jackson 2);
    // disable it so trailing content after a valid JSON value doesn't fail validation
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<JsonString> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            OBJECT_MAPPER.readTree(value);
        } catch (JacksonException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("invalid json '({validatedValue})': " + e.getMessage())
                .addConstraintViolation();

            return false;
        }
        return true;
    }
}
