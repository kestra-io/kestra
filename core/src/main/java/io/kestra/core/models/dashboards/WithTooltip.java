package io.kestra.core.models.dashboards;

import io.kestra.core.models.dashboards.charts.TooltipBehaviour;

public interface WithTooltip {
    String TOOLTIP_TITLE = "Values listed in the tooltip: all of them (`ALL`), only the hovered one (`SINGLE`) or none (`NONE`).";

    TooltipBehaviour getTooltip();
}
