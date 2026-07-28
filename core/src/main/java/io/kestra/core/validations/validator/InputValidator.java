package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.validations.InputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Introspected
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

        return true;
    }
}