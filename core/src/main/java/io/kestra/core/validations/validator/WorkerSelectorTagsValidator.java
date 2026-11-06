package io.kestra.core.validations.validator;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.utils.PebbleUtil;
import io.kestra.core.validations.WorkerSelectorTagsValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public final class WorkerSelectorTagsValidator implements ConstraintValidator<WorkerSelectorTagsValidation, Property<List<String>>> {

    @Override
    public boolean isValid(
        @Nullable Property<List<String>> value,
        @NonNull AnnotationValue<WorkerSelectorTagsValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        Optional<List<String>> tags;
        try {
            tags = Property.asStaticList(value, String.class);
        } catch (IllegalVariableEvaluationException e) {
            return reject(context, "must be a list of tags or a Pebble expression");
        }
        if (tags.isEmpty()) {
            return true;
        }
        if (tags.get().size() > WorkerSelector.MAX_TAGS) {
            return reject(context, "must hold at most %d tags, got %d".formatted(WorkerSelector.MAX_TAGS, tags.get().size()));
        }
        List<String> invalid = tags.get().stream()
            .filter(tag -> tag == null || (!PebbleUtil.containsOpeningBlockDelimiter(tag) && !Rfc1123LabelValidator.isValid(tag)))
            .toList();
        if (!invalid.isEmpty()) {
            return reject(
                context, "must be RFC 1123 labels (lowercase alphanumerics and hyphens, "
                    + "must start and end with alphanumeric, max 63 chars) or Pebble expressions, got "
                    + invalid.toString().replaceAll("([{}$\\\\])", "\\\\$1")
            );
        }
        return true;
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
