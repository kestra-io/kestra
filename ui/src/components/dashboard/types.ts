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
