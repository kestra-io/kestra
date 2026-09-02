import {ChartChartOption, DashboardControllerDashboardResponse} from "@kestra-io/kestra-sdk"

export interface Dashboard extends DashboardControllerDashboardResponse {
    charts: Chart[]
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
        [key: string]: unknown;
    };
    data?: {
        columns?: {
            [key: string]: Record<string, any>;
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

/**
 * The dashboards a tenant serves by default. Only an edition that can store dashboards has any,
 * so the schema is Enterprise-only and absent from the open-source SDK — hence declared here,
 * for the store shared by both editions.
 */
export interface DashboardSettings {
    defaultHomeDashboard?: string;
    defaultFlowOverviewDashboard?: string;
    defaultNamespaceOverviewDashboard?: string;
}
