package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.plugin.core.dashboard.chart.kpis.KpiOption;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@Schema(
    title = "Track a specific value."
)
public class KPI <F extends Enum<F>, D extends DataFilterKPI<F, ? extends ColumnDescriptor<F>>> extends DataChartKPI<KpiOption, D> {

    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }

    @Override
    public Integer maxNumberOfAggregations() {
        return 1;
    }
}
