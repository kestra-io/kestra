package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.plugin.core.dashboard.chart.tables.TableColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.tables.TableOption;
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
public class Table<F extends Enum<F>, D extends DataFilter<F, ? extends TableColumnDescriptor<F>>> extends DataChart<TableOption, D> {
}
