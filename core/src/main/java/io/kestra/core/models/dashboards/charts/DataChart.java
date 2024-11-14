package io.kestra.core.models.dashboards.charts;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ChartOption;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.validations.DataChartValidation;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@DataChartValidation
public abstract class DataChart<P extends ChartOption, D extends DataFilter<?, ?>> extends Chart<P> implements io.kestra.core.models.Plugin {
    @NotNull
    private D data;

    public Integer minNumberOfAggregations() {
        return null;
    }

    public Integer maxNumberOfAggregations() {
        return null;
    }
}
