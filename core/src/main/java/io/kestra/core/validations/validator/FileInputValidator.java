package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.validations.FileInputValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class FileInputValidator implements ConstraintValidator<FileInputValidation, FileInput> {
    @Override
    public boolean isValid(@Nullable FileInput value, @NonNull AnnotationValue<FileInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }

        if (value.getDefaults() != null && !LocalPath.FILE_SCHEME.equals(value.getDefaults().getScheme())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("inputs of type 'FILE' only support `defaults` as local files using a file URI")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}