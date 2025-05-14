package io.kestra.core.validations.validator;

import io.kestra.core.validations.KPIChartValidation;
import io.kestra.plugin.core.dashboard.chart.KPI;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class KPIChartValidator  implements ConstraintValidator<KPIChartValidation, KPI<?, ?>> {

    @Override
    public boolean isValid(
        KPI<?, ?> value,
        io.micronaut.core.annotation.AnnotationValue<KPIChartValidation> annotationMetadata,
        io.micronaut.validation.validator.constraints.ConstraintValidatorContext context) {

        if (value.getData() != null && value.getData().getColumns() != null) {
            if (value.getData().getColumns().size() > 1) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("KPI chart can only have one column.")
                    .addConstraintViolation();
                return false;
            }
        }
        if (value.getData() != null && value.getData().getOrderBy() != null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("KPI chart can not have any orderBy.")
                    .addConstraintViolation();
                return false;
        }

        return true;
    }
}
