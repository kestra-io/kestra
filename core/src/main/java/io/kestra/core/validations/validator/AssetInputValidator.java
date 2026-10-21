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
        if (value == null || value.getAssetId() == null || PebbleUtil.containsOpeningBlockDelimiter(value.getAssetId())) {
            return true;
        }

        Optional<String> violation = RegexUtils.isSafeUserRegex(value.getAssetId())
            ? RegexUtils.syntaxError(value.getAssetId()).map(error -> "`assetId` is not a valid regular expression: " + error)
            : Optional.of("`assetId` is too long or prone to catastrophic backtracking");
        if (violation.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(violation.get()).addConstraintViolation();
        return false;
    }
}
