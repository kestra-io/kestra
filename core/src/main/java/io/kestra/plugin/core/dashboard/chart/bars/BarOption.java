package io.kestra.plugin.core.dashboard.chart.bars;

import io.kestra.core.models.dashboards.ChartOption;
import io.kestra.core.models.dashboards.WithLegend;
import io.kestra.core.models.dashboards.WithTooltip;
import io.kestra.core.models.dashboards.charts.LegendOption;
import io.kestra.core.models.dashboards.charts.TooltipBehaviour;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class BarOption extends ChartOption implements WithLegend, WithTooltip {
    @Builder.Default
    private TooltipBehaviour tooltip = TooltipBehaviour.ALL;

    @Builder.Default
    private LegendOption legend = LegendOption.builder().build();
}
