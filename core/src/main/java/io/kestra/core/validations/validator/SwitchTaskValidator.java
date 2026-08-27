package io.kestra.core.validations.validator;

import java.util.Map;
import java.util.Optional;

import io.kestra.core.validations.SwitchTaskValidation;
import io.kestra.plugin.core.flow.Switch;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class SwitchTaskValidator implements ConstraintValidator<SwitchTaskValidation, Switch> {
    @Override
    public boolean isValid(
        @Nullable Switch value,
        @NonNull AnnotationValue<SwitchTaskValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (
            (value.getCases() == null || value.getCases().isEmpty()) &&
                (value.getDefaults() == null || value.getDefaults().isEmpty())
        ) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("No task defined, neither cases or default have any tasks")
                .addConstraintViolation();

            return false;
        }

        Optional<String> emptyCase = value.getCases() == null
            ? Optional.empty()
            : value.getCases()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (emptyCase.isPresent()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("The case '%s' must define at least one task.".formatted(emptyCase.get()))
                .addConstraintViolation();

            return false;
        }

        if (value.getDefaults() != null && value.getDefaults().isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("The 'defaults' property cannot be empty.")
                .addConstraintViolation();

            return false;
        }

        return true;
    }
}
