export type Dashboard = {
    id: string;
    title?: string;
    sourceCode?: string;
    [key: string]: unknown;
};

export type Chart = {
    id: string;
    type: string;
    content?: string;
    chartOptions?: {
        displayName?: string;
        description?: string;
        width?: number;
        [key: string]: unknown;
    };
    [key: string]: unknown;
};

import Bar from "../sections/Bar.vue";
import KPI from "../sections/KPI.vue";
import Markdown from "../sections/Markdown.vue";
import Pie from "../sections/Pie.vue";
import Table from "../sections/Table.vue";
import TimeSeries from "../sections/TimeSeries.vue";

export const TYPES: Record<string, any> = {
    "io.kestra.plugin.core.dashboard.chart.Bar": Bar,
    "io.kestra.plugin.core.dashboard.chart.KPI": KPI,
    "io.kestra.plugin.core.dashboard.chart.Markdown": Markdown,
    "io.kestra.plugin.core.dashboard.chart.Pie": Pie,
    "io.kestra.plugin.core.dashboard.chart.Table": Table,
    "io.kestra.plugin.core.dashboard.chart.TimeSeries": TimeSeries,
};

export const isKPIChart = (type: string) => type === "io.kestra.plugin.core.dashboard.chart.KPI";
