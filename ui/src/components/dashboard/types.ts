import type {DashboardControllerDashboardResponse, ChartFiltersOverrides, DashboardControllerPreviewRequest} from "@kestra-io/kestra-sdk"
import type {FilterObject} from "../../utils/filters"

// charts is overridden: chart definitions are polymorphic (DataChart, DataChartKPI, ...) and the
// backend returns the full Chart shape below, but the SDK's generated response type only models
// the lean common subset (id/type/chartOptions). title/deleted are widened back to optional: a
// dashboard can be a locally-built preview placeholder that never persisted either field.
export type Dashboard = Omit<DashboardControllerDashboardResponse, "charts" | "title" | "deleted"> & {
    title?: string;
    deleted?: boolean;
    charts: Chart[];
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

export type Request = DashboardControllerPreviewRequest;

// filters is overridden: FilterObject's field/operation are plain strings (as produced by this
// app's filter bar), not the SDK's strict QueryFilterField/QueryFilterOp enums - the same gap
// routeQueryToQueryFilters() bridges for other stores' search endpoints.
export type Parameters = Omit<ChartFiltersOverrides, "filters"> & {
    filters?: FilterObject[];
};
