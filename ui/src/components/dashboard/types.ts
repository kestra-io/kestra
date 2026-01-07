export type Dashboard = {
    id: string;
    charts: Chart[];
    title?: string;
    sourceCode?: string;
    [key: string]: unknown;
};

export type Chart = {
    id: string;
    type: string;
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

export type Request = {
    chart: Chart["content"];
    globalFilter?: Parameters;
};

export type Parameters = {
    pageNumber?: number;
    pageSize?: number;
    startDate?: Date;
    endDate?: Date;
    namespace?: string;
    labels?: Record<string, string>;
    filters?: Record<string, any>;
};