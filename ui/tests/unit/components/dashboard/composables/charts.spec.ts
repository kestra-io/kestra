import {beforeEach, describe, expect, it} from "vitest";
import {createPinia, setActivePinia} from "pinia";
import {chartSegmentDrillDown, pushChartDrillDown} from "../../../../../src/components/dashboard/composables/charts.js";

const EXECUTIONS_CHART = {
    data: {
        type: "io.kestra.plugin.core.dashboard.data.Executions",
        columns: {
            date: {field: "START_DATE", displayName: "Date"},
            state: {field: "STATE"},
            total: {field: "ID", agg: "COUNT"},
        },
    },
};

describe("chartSegmentDrillDown", () => {
    it("should map a STATE dimension to the executions state filter", () => {
        const result = chartSegmentDrillDown(EXECUTIONS_CHART, {field: "STATE"}, "FAILED");

        expect(result).toEqual({
            name: "executions/list",
            timeFiltered: true,
            query: {"filters[state][IN]": "FAILED"},
        });
    });

    it("should extract the state from a comma-joined series label", () => {
        const result = chartSegmentDrillDown(EXECUTIONS_CHART, {field: "STATE"}, "company.team, FAILED");

        expect(result?.query).toEqual({"filters[state][IN]": "FAILED"});
    });

    it("should return null for data sources without a list to drill into", () => {
        const chart = {data: {type: "io.kestra.plugin.core.dashboard.data.Metrics", columns: {}}};

        expect(chartSegmentDrillDown(chart, {field: "STATE"}, "FAILED")).toBeNull();
    });
});

describe("pushChartDrillDown", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
    });

    it("should push the list route with scope, pagination and the default time range", () => {
        const pushed: Record<string, any>[] = [];
        const router = {push: (to: Record<string, any>) => pushed.push(to)};
        const route = {params: {tenant: "main"}};

        pushChartDrillDown(router, route, {
            name: "executions/list",
            timeFiltered: true,
            query: {"filters[state][IN]": "FAILED"},
        });

        expect(pushed).toEqual([{
            name: "executions/list",
            params: {tenant: "main"},
            query: {
                "filters[state][IN]": "FAILED",
                scope: "USER",
                size: 100,
                page: 1,
                "filters[timeRange][EQUALS]": "PT24H",
            },
        }]);
    });

    it("should merge extra filters and omit the time range when the list does not support it", () => {
        const pushed: Record<string, any>[] = [];
        const router = {push: (to: Record<string, any>) => pushed.push(to)};
        const route = {params: {tenant: "main"}};

        pushChartDrillDown(router, route, {
            name: "flows/list",
            timeFiltered: false,
            query: {},
        }, {"filters[namespace][IN]": "company.team"});

        expect(pushed[0].query).toEqual({
            "filters[namespace][IN]": "company.team",
            scope: "USER",
            size: 100,
            page: 1,
        });
    });
});
