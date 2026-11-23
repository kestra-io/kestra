import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"

const chartPreview = vi.fn()
vi.mock("vue-router", () => ({
    useRoute: () => ({
        name: "executions/list",
        params: {},
        query: {"filters[kind][EQUALS]": "NORMAL", "filters[namespace][EQUALS]": "company.team"},
    }),
}))
vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({generate: vi.fn(), chartPreview}),
}))

import {useChartGenerator} from "./useDashboards"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import type {Chart} from "../types"

const chart: Chart = {id: "total", type: "io.kestra.plugin.core.dashboard.chart.TimeSeries", content: "id: total"}

describe("useChartGenerator kind filter", () => {
    it("should send no kind filter when the route selects only NORMAL, so normal runs stored without a kind are counted", async () => {
        chartPreview.mockResolvedValue({results: [], total: 0})

        i18nMount(defineComponent({
            setup() {
                useChartGenerator(undefined, {chart, filters: [], showDefault: true}, true)
                return () => h("div")
            },
        }))

        await vi.waitFor(() => expect(chartPreview).toHaveBeenCalled())
        const filters = chartPreview.mock.calls[0][0].globalFilter.filters
        expect(filters.map((filter: {field: string}) => filter.field)).toEqual(["namespace"])
    })
})
