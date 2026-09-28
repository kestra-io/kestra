package io.kestra.core.validations.validator;

import java.util.List;

import io.kestra.core.models.flows.input.TableInput;
import io.kestra.core.validations.TableInputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class TableInputValidator implements ConstraintValidator<TableInputValidation, TableInput> {
    @Override
    public boolean isValid(@Nullable TableInput value, @NonNull AnnotationValue<TableInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = RecordFieldsValidator.violations("columns", value.getColumns());
        TableInput.Rows rows = value.getRows();
        if (rows != null && rows.min() != null && rows.max() != null && rows.min() > rows.max()) {
            violations.add("`rows.min` cannot be greater than `rows.max`");
        }
        return RecordFieldsValidator.report(violations, context);
    }
}
