package io.kestra.core.validations.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.validations.JsonString;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
@Introspected
public class JsonStringValidator  implements ConstraintValidator<JsonString, String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        } catch (IOException e) {
            context.messageTemplate("invalid json '({validatedValue})': " + e.getMessage());

            return false;
        }
        return true;
    }
}
