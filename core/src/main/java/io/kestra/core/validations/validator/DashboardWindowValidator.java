package io.kestra.core.validations.validator;

import io.kestra.core.models.dashboards.TimeWindow;
import io.kestra.core.validations.DashboardWindowValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
@Introspected
public class DashboardWindowValidator implements ConstraintValidator<DashboardWindowValidation, TimeWindow> {

    @Override
    public boolean isValid(
        @Nullable TimeWindow value,
        @NonNull AnnotationValue<DashboardWindowValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = new ArrayList<>();

        if (value.getMax() != null && value.getDefaultDuration() != null && value.getDefaultDuration().compareTo(value.getMax()) > 0) {
            violations.add("Default duration can't exceed max duration");
        }

        if (!violations.isEmpty()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid data chart: " + String.join(", ", violations))
                .addConstraintViolation();
            return false;
        } else {
            return true;
        }
    }
}
