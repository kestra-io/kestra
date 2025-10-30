package io.kestra.core.validations.validator;

import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.storages.Namespace;
import io.kestra.core.validations.FileInputValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;

@Singleton
@Introspected
public class FileInputValidator implements ConstraintValidator<FileInputValidation, FileInput> {
    
    @Inject
    VariableRenderer variableRenderer;
    
    @Override
    public boolean isValid(@Nullable FileInput value, @NonNull AnnotationValue<FileInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true; // nulls are allowed according to spec
        }
        
        if (value.getDefaults() != null) {
            PropertyContext propertyContext = PropertyContext.create(variableRenderer);
            try {
                URI uri = Property.as(value.getDefaults(), propertyContext, URI.class);
                if (uri != null && !LocalPath.FILE_SCHEME.equals(uri.getScheme()) && !Namespace.NAMESPACE_FILE_SCHEME.equals(uri.getScheme())) {
                    context.disableDefaultConstraintViolation();
                    context
                        .buildConstraintViolationWithTemplate("inputs of type 'FILE' only support `defaults` as local files using a file URI or as namespace files using a nsfile URI")
                        .addConstraintViolation();
                    return false;
                }
            } catch (Exception ignore) {
                context.disableDefaultConstraintViolation();
                context
                    .buildConstraintViolationWithTemplate("inputs of type 'FILE' only support `defaults` with expression that can be rendered immediately")
                    .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}