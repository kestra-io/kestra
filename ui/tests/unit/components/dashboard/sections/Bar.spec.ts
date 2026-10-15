import {describe, test, expect, vi} from "vitest";
import {ref} from "vue";
import {shallowMount} from "@vue/test-utils";
import {createPinia} from "pinia";
import {Bar as ChartJsBar} from "vue-chartjs";

const results = ref<Record<string, unknown>[]>([]);

vi.mock("vue-router", async (importOriginal) => ({
    ...(await importOriginal<typeof import("vue-router")>()),
    useRoute: () => ({name: "home", params: {}}),
    useRouter: () => ({push: vi.fn()}),
}));

vi.mock("../../../../../src/components/dashboard/composables/useDashboards", () => ({
    getDashboard: () => "default",
    useChartGenerator: () => ({data: ref({results: results.value}), generate: vi.fn()}),
}));

import Bar from "../../../../../src/components/dashboard/sections/Bar.vue";
import type {Chart} from "../../../../../src/components/dashboard/composables/useDashboards";

const chart = {
    id: "executions_per_namespace_bars",
    type: "io.kestra.plugin.core.dashboard.chart.Bar",
    chartOptions: {column: "namespace", legend: {enabled: false}},
    data: {
        columns: {
            namespace: {field: "NAMESPACE"},
            state: {field: "STATE"},
            total: {agg: "COUNT"},
        },
    },
};

const mountWith = (rows: Record<string, unknown>[]) => {
    results.value = rows;
    return shallowMount(Bar, {props: {chart: chart as unknown as Chart}, global: {plugins: [createPinia()]}});
};

describe("dashboard Bar.vue", () => {
    test("builds one dataset per series with a value for every x-axis label", () => {
        const namespaces = Array.from({length: 300}, (_, i) => `perf.team_${i}`);
        const rows = namespaces.flatMap((namespace) => [
            {namespace, state: "SUCCESS", total: 2},
            {namespace, state: "FAILED", total: 1},
        ]);
        rows.push({namespace: "perf.only_success", state: "SUCCESS", total: 5});

        const data = mountWith(rows).findComponent(ChartJsBar).props("data");

        expect(data.labels).toHaveLength(301);
        expect(data.datasets.map((dataset) => dataset.label)).toEqual(["SUCCESS", "FAILED"]);
        expect(data.datasets[0].data).toHaveLength(301);
        expect(data.datasets[0].data[0]).toBe(2);
        expect(data.datasets[1].data[0]).toBe(1);
        expect(data.datasets[0].data[300]).toBe(5);
        expect(data.datasets[1].data[300]).toBe(0);
    });

    test("builds a single dataset when the chart has no series column", () => {
        results.value = [{namespace: "company.a", total: 4}, {namespace: "company.b", total: 7}];
        const data = shallowMount(Bar, {
            props: {chart: {...chart, data: {columns: {namespace: {field: "NAMESPACE"}, total: {agg: "COUNT"}}}} as unknown as Chart},
            global: {plugins: [createPinia()]},
        }).findComponent(ChartJsBar).props("data");

        expect(data.labels).toEqual(["company.a", "company.b"]);
        expect(data.datasets).toHaveLength(1);
        expect(data.datasets[0].data).toEqual([4, 7]);
    });

    test("shows the hovered series and its value in the tooltip", () => {
        const options = mountWith([{namespace: "company.team", state: "SUCCESS", total: 3}]).findComponent(ChartJsBar).props("options");

        const label = options?.plugins?.tooltip?.callbacks?.label as unknown as (item: {dataset: {label: string}, raw: number}) => string;

        expect(label({dataset: {label: "SUCCESS"}, raw: 3})).toBe("(SUCCESS): total = 3");
    });
});
