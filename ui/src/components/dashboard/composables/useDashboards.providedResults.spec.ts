import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"

const generate = vi.fn()
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "dashboards", params: {}, query: {}}),
}))
vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({generate, chartPreview: vi.fn()}),
}))

import {CHART_RESULTS_KEY, useChartGenerator} from "./useDashboards"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import type {Chart, ChartResults} from "../types"

const chart: Chart = {id: "rows_loaded", type: "io.kestra.plugin.core.dashboard.chart.KPI"}

function mountGenerator(provided?: (chartId: string, pagination?: {pageNumber: number; pageSize: number}) => ChartResults | undefined, pagination?: {pageNumber: number; pageSize: number}) {
    let data: ReturnType<typeof useChartGenerator>["data"] | undefined
    const Probe = defineComponent({
        setup() {
            const generator = useChartGenerator("dashboard-id", {chart, filters: [], showDefault: false}, !pagination)
            data = generator.data
            if (pagination) void generator.generate(pagination)
            return () => h("div")
        },
    })
    i18nMount(Probe, {global: {provide: provided ? {[CHART_RESULTS_KEY as symbol]: provided} : {}}})
    return () => data?.value
}

describe("useChartGenerator with provided results", () => {
    it("answers from the host's rows, without calling the dashboard API", async () => {
        generate.mockClear()

        const data = mountGenerator((chartId) => ({results: [{value: chartId}], total: 1}))
        await vi.waitFor(() => expect(data()).toEqual({results: [{value: "rows_loaded"}], total: 1}))

        expect(generate).not.toHaveBeenCalled()
    })

    it("hands the requested page to the host", async () => {
        const data = mountGenerator((_chartId, pagination) => ({results: [{page: pagination?.pageNumber}], total: 9}), {pageNumber: 2, pageSize: 5})
        await vi.waitFor(() => expect(data()).toEqual({results: [{page: 2}], total: 9}))
    })

    it("still calls the dashboard API when no host provides rows", async () => {
        generate.mockClear()
        generate.mockResolvedValue({results: [{value: 1}], total: 1})

        const data = mountGenerator()
        await vi.waitFor(() => expect(data()).toEqual({results: [{value: 1}], total: 1}))

        expect(generate).toHaveBeenCalledWith("dashboard-id", "rows_loaded", expect.anything())
    })
})
