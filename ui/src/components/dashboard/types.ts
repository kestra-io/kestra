import {ChartChartOption, DashboardControllerDashboardResponse} from "@kestra-io/kestra-sdk"

export interface Dashboard extends DashboardControllerDashboardResponse {
    charts: Chart[]
}

/** A dashboard chart's data-column config, as declared under `data.columns` in the chart's YAML. Shape is plugin-driven (not modeled in the OpenAPI schema), so unknown properties stay accessible through the index signature. */
export interface Column {
    field?: string;
    key?: string;
    agg?: string;
    displayName?: string;
    graphStyle?: string;
    [key: string]: unknown;
}

export interface Chart extends ChartChartOption {
    chartOptions?: {
        displayName?: string;
        description?: string;
        width?: number;
        pagination?: {
            enabled?: boolean;
            [key: string]: unknown;
        };
        legend?:{
            enabled?: boolean;
        };
        column: string;
        /** Bar.vue: caps how many stacked-bar categories render before collapsing the rest into "Others". */
        limit?: number;
        [key: string]: unknown;
    };
    data?: {
        columns?: {
            [key: string]: Column;
        };
        [key: string]: unknown;
    };
    content?: string;
    source?: {
        type?: string;
        content?: string;
        [key: string]: unknown;
    };

    [key: string]: unknown;
};

/** The tenant's default dashboards. The schema is Enterprise-only and absent from the OSS SDK, so it is declared here for the store shared by both editions. */
export interface DashboardSettings {
    defaultHomeDashboard?: string;
    defaultFlowOverviewDashboard?: string;
    defaultNamespaceOverviewDashboard?: string;
}
