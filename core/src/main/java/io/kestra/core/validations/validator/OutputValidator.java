package io.kestra.core.validations.validator;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.OutputType;
import io.kestra.core.validations.OutputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class OutputValidator implements ConstraintValidator<OutputValidation, Output> {
    @Override
    public boolean isValid(@Nullable Output value, @NonNull AnnotationValue<OutputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (OutputType.UNKNOWN == value.getType()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "The output '%s' has an unknown type, it must be one of %s.".formatted(
                    value.getId(), Arrays.stream(OutputType.values()).filter(type -> OutputType.UNKNOWN != type).map(OutputType::name).collect(Collectors.joining(", "))
                )
            ).addConstraintViolation();
            return false;
        }

        if (Boolean.TRUE.equals(value.getSearchable()) && value.getType() != null && !Output.SEARCHABLE_TYPES.contains(value.getType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "The output '%s' cannot be searchable: the type %s is not supported, a searchable output must be one of %s.".formatted(
                    value.getId(), value.getType(), Output.SEARCHABLE_TYPES.stream().map(OutputType::name).collect(Collectors.joining(", "))
                )
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}
