package io.kestra.core.models.dashboards;

import io.kestra.core.models.dashboards.charts.TooltipBehaviour;

public interface WithTooltip {
    TooltipBehaviour getTooltip();
}
