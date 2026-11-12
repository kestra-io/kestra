package io.kestra.core.models.dashboards;

import java.util.List;

import io.kestra.core.models.dashboards.charts.ColorByValue;

public interface WithColorByValues {
    String COLOR_BY_VALUES_TITLE = "Label, color and order of the chart's values.";
    String COLOR_BY_VALUES_DESCRIPTION = """
        Each entry matches one value of the column the chart is split by, such as `by_priority.critical`.
        Listed values come first in the chart and its legend, in the order they are listed; the others follow and get a color from the theme's chart palette.""";

    List<ColorByValue> getColorByValues();
}
