import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {chartPreview, generate} = vi.hoisted(() => ({chartPreview: vi.fn(), generate: vi.fn()}))

vi.mock("../../../stores/dashboard", () => ({
    useDashboardStore: () => ({chartPreview, generate}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}, fullPath: "/dashboards/default"}),
    useRouter: () => ({push: vi.fn()}),
}))

import Table from "./Table.vue"

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const executionsChart = {
    id: "recent_executions",
    type: "io.kestra.plugin.core.dashboard.chart.Table",
    chartOptions: {displayName: "Executions", pagination: {enabled: true}},
    content: "id: recent_executions\n",
    data: {
        type: EXECUTIONS,
        columns: {
            id: {field: "ID", displayName: "Execution ID"},
            state: {field: "STATE", displayName: "State"},
        },
    },
}

const mountTable = (showDefault: boolean) =>
    i18nMount(Table, {
        messages: {dashboards: {quick_filters: {upcoming: "Upcoming"}}},
        props: {chart: executionsChart as never, showDefault},
        global: {
            stubs: {
                KsDataTable: {name: "KsDataTable", props: ["rowKey"], template: "<div><slot /></div>"},
                KsTableColumn: {name: "KsTableColumn", props: ["label"], template: "<div class='column-header'>{{ label }}</div>"},
                KsNoData: true,
                Motion: true,
            },
        },
    })

const tabs = (wrapper: ReturnType<typeof mountTable>) => wrapper.findAll("button.quick-filter-tab")

const columnLabels = (wrapper: ReturnType<typeof mountTable>) =>
    wrapper.findAll(".column-header").map((column) => column.text())

describe("Table Upcoming tab", () => {
    beforeEach(() => {
        chartPreview.mockReset()
        chartPreview.mockResolvedValue({results: [{namespace: "io.kestra"}], total: 1})
    })

    afterEach(() => sessionStorage.clear())

    it("offers no Upcoming tab on a stored dashboard", async () => {
        const wrapper = mountTable(false)
        await flushPromises()

        expect(tabs(wrapper).map((tab) => tab.text())).not.toContain("Upcoming")
    })

    it("requests the Triggers chart and swaps the columns when Upcoming is selected", async () => {
        const wrapper = mountTable(true)
        await flushPromises()

        expect(columnLabels(wrapper)).toEqual(["Execution ID", "State"])
        expect(chartPreview.mock.calls[0][0].chart).toBe(executionsChart.content)

        await tabs(wrapper).at(-1)!.trigger("click")
        await flushPromises()

        const requested = chartPreview.mock.calls.at(-1)![0].chart as string
        expect(requested).toContain("io.kestra.plugin.core.dashboard.data.Triggers")
        expect(requested).toContain("NEXT_EXECUTION_DATE")
        expect(columnLabels(wrapper)).toEqual(["namespace", "flow", "trigger", "next_execution"])
    })

    it("goes back to the Executions chart and columns when All is selected again", async () => {
        const wrapper = mountTable(true)
        await flushPromises()

        await tabs(wrapper).at(-1)!.trigger("click")
        await flushPromises()
        await tabs(wrapper)[0].trigger("click")
        await flushPromises()

        expect(chartPreview.mock.calls.at(-1)![0].chart).toBe(executionsChart.content)
        expect(columnLabels(wrapper)).toEqual(["Execution ID", "State"])
    })

    it("keys rows on their own values when the data source has no ID column", async () => {
        chartPreview.mockResolvedValue({
            results: [{namespace: "io.kestra", flowId: "a"}, {namespace: "io.kestra", flowId: "b"}],
            total: 2,
        })
        const wrapper = mountTable(true)
        await flushPromises()

        await tabs(wrapper).at(-1)!.trigger("click")
        await flushPromises()

        const keyOf = wrapper.findComponent({name: "KsDataTable"}).props("rowKey") as (row: Record<string, unknown>) => string
        expect(keyOf({namespace: "io.kestra", flowId: "a"})).not.toBe(keyOf({namespace: "io.kestra", flowId: "b"}))
        expect(keyOf({id: "exec-1", namespace: "io.kestra"})).toBe("exec-1")
    })

    it("exports the chart of the tab currently shown", async () => {
        const wrapper = mountTable(true)
        await flushPromises()

        expect(wrapper.vm.exportChart().id).toBe("recent_executions")

        await tabs(wrapper).at(-1)!.trigger("click")
        await flushPromises()

        expect(wrapper.vm.exportChart().id).toBe("recent_executions__upcoming")
    })

    it("keeps a page size per tab and reopens each tab on its first page", async () => {
        const wrapper = mountTable(true)
        await flushPromises()

        wrapper.findComponent({name: "KsDataTable"}).vm.$emit("page-changed", {page: 2, size: 10})
        await flushPromises()

        await tabs(wrapper).at(-1)!.trigger("click")
        await flushPromises()

        expect(chartPreview.mock.calls.at(-1)![0].globalFilter).toMatchObject({pageNumber: 1, pageSize: 25})

        await tabs(wrapper)[0].trigger("click")
        await flushPromises()

        expect(chartPreview.mock.calls.at(-1)![0].globalFilter).toMatchObject({pageNumber: 1, pageSize: 10})
    })
})
