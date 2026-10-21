package io.kestra.core.validations.validator;

import java.util.Optional;

import io.kestra.core.models.flows.input.AssetInput;
import io.kestra.core.utils.PebbleUtil;
import io.kestra.core.utils.RegexUtils;
import io.kestra.core.validations.AssetInputValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class AssetInputValidator implements ConstraintValidator<AssetInputValidation, AssetInput> {
    @Override
    public boolean isValid(@Nullable AssetInput value, @NonNull AnnotationValue<AssetInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Optional<String> violation = assetIdViolation(value.getAssetId());
        if (violation.isEmpty() && Boolean.TRUE.equals(value.getEditableProperties()) && Boolean.TRUE.equals(value.getMultiple())) {
            violation = Optional.of("`editableProperties` cannot be used with `multiple`");
        }
        if (violation.isEmpty() && AssetInput.PersistOverrides.UNKNOWN == value.getPersistOverrides()) {
            violation = Optional.of("`persistOverrides` must be one of NONE, UPDATE or CLONE");
        }
        if (violation.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violation.get()).addConstraintViolation();
        return false;
    }

    private static Optional<String> assetIdViolation(@Nullable String assetId) {
        if (assetId == null || PebbleUtil.containsOpeningBlockDelimiter(assetId)) {
            return Optional.empty();
        }
        return RegexUtils.isSafeUserRegex(assetId)
            ? RegexUtils.syntaxError(assetId).map(error -> "`assetId` is not a valid regular expression: " + error)
            : Optional.of("`assetId` is too long or prone to catastrophic backtracking");
    }
}
