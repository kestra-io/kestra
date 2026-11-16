package io.kestra.plugin.core.dashboard.chart.pies;

import java.util.Collections;
import java.util.List;

import io.kestra.core.models.dashboards.ChartOption;
import io.kestra.core.models.dashboards.WithColorByValues;
import io.kestra.core.models.dashboards.WithLegend;
import io.kestra.core.models.dashboards.WithTooltip;
import io.kestra.core.models.dashboards.charts.ColorByValue;
import io.kestra.core.models.dashboards.charts.LegendOption;
import io.kestra.core.models.dashboards.charts.TooltipBehaviour;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class PieOption extends ChartOption implements WithLegend, WithTooltip, WithColorByValues {
    @Builder.Default
    private TooltipBehaviour tooltip = TooltipBehaviour.ALL;

    @Builder.Default
    private LegendOption legend = LegendOption.builder().build();

    @Builder.Default
    private PieGraphStyle graphStyle = PieGraphStyle.DONUT;

    private String colorByColumn;

    @Schema(title = WithColorByValues.COLOR_BY_VALUES_TITLE, description = WithColorByValues.COLOR_BY_VALUES_DESCRIPTION)
    private List<@Valid ColorByValue> colorByValues;

    @Override
    public List<String> neededColumns() {
        return colorByColumn == null ? Collections.emptyList() : List.of(colorByColumn);
    }

    enum PieGraphStyle {
        PIE,
        DONUT
    }
}
