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


        return true;
    }
}
