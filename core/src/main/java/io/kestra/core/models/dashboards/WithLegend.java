package io.kestra.core.models.dashboards;

import io.kestra.core.models.dashboards.charts.LegendOption;

public interface WithLegend {
    String LEGEND_TITLE = "Legend shown next to the chart.";

    LegendOption getLegend();
}
