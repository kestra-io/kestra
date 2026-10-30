package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.validations.InputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.stream.Collectors;

@Singleton
public class InputValidator implements ConstraintValidator<InputValidation, Input<?>> {

    @Inject
    VariableRenderer variableRenderer;

    @Override
    public boolean isValid(@Nullable Input<?> value, @NonNull AnnotationValue<InputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (value instanceof FormInput form) {
            if (value.getDefaults() != null || value.getPrefill() != null) {
                context.disableDefaultConstraintViolation();
                context
                    .buildConstraintViolationWithTemplate("A FORM input groups other inputs and cannot declare a default value or a prefill.")
                    .addConstraintViolation();
                return false;
            }
            if (form.getInputs() != null && form.getInputs().stream().anyMatch(child -> child instanceof FormInput)) {
                context.disableDefaultConstraintViolation();
                context
                    .buildConstraintViolationWithTemplate("A FORM input cannot contain another FORM input; grouping is limited to a single level.")
                    .addConstraintViolation();
                return false;
            }
        }

        if (value.getDefaults() != null && Boolean.FALSE.equals(value.getRequired())) {
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate("Inputs with a default value must be required, since the default is always applied.")
                .addConstraintViolation();
            return false;
        }

        if (value.getDefaults() != null && value.getPrefill() != null) {
            context.disableDefaultConstraintViolation();
            context
                .buildConstraintViolationWithTemplate("Inputs with a default value cannot also have a prefill.")
                .addConstraintViolation();
            return false;
        }

        if (Boolean.TRUE.equals(value.getSearchable()) && value.getType() != null && !Data.SEARCHABLE_TYPES.contains(value.getType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "The input '%s' cannot be searchable: the type %s is not supported, a searchable input must be one of %s.".formatted(
                    value.getId(), value.getType(), Data.SEARCHABLE_TYPES.stream().map(Type::name).collect(Collectors.joining(", "))
                )
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}