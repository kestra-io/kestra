import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {generate, generateStateCounts} = vi.hoisted(() => ({generate: vi.fn(), generateStateCounts: vi.fn()}))

vi.mock("../composables/useDashboards", () => ({
    useChartGenerator: () => ({EMPTY_TEXT: "", generate, generateStateCounts}),
    isPaginationEnabled: () => true,
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

import Table from "./Table.vue"

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const mountTable = (type = EXECUTIONS) =>
    i18nMount(Table, {
        props: {
            dashboardId: "d1",
            chart: {
                id: "executions",
                type: "io.kestra.plugin.core.dashboard.chart.Table",
                data: {type, columns: {state: {field: "STATE"}}},
            },
        },
        global: {
            stubs: {KsDataTable: true, KsTableColumn: true, KsNoData: true, TableQuickFilter: true, Motion: true},
        },
    })

const tabCounts = (wrapper: ReturnType<typeof mountTable>) =>
    wrapper.findComponent({name: "TableQuickFilter"}).props("counts")

describe("Table quick filter counts", () => {
    beforeEach(() => {
        generate.mockReset()
        generate.mockResolvedValue({results: [{state: "SUCCESS"}], total: 12})
        generateStateCounts.mockReset()
        generateStateCounts.mockResolvedValue({counts: {SUCCESS: 9, FAILED: 2, RUNNING: 1}})
    })

    afterEach(() => sessionStorage.clear())

    it("counts once, after the rows are loaded, and hands the totals to the tabs", async () => {
        const wrapper = mountTable()
        await flushPromises()

        expect(generateStateCounts).toHaveBeenCalledTimes(1)
        expect(generate.mock.invocationCallOrder[0]).toBeLessThan(generateStateCounts.mock.invocationCallOrder[0])
        expect(tabCounts(wrapper)).toEqual({all: 12, running: 1, paused: 0, success: 9, warning: 0, failed: 2})
    })

    it("does not count again when paging", async () => {
        const wrapper = mountTable()
        await flushPromises()

        wrapper.findComponent({name: "KsDataTable"}).vm.$emit("page-changed", {page: 2, size: 10})
        await flushPromises()

        expect(generate).toHaveBeenCalledTimes(2)
        expect(generateStateCounts).toHaveBeenCalledTimes(1)
    })

    it("does not ask for counts for a chart without quick filters", async () => {
        const wrapper = mountTable("io.kestra.plugin.core.dashboard.data.Logs")
        await flushPromises()

        expect(generateStateCounts).not.toHaveBeenCalled()
        expect(tabCounts(wrapper)).toEqual({})
    })

    it("leaves the tabs without counts when the request fails", async () => {
        generateStateCounts.mockRejectedValue(new Error("boom"))

        const wrapper = mountTable()
        await flushPromises()

        expect(tabCounts(wrapper)).toEqual({})
    })
})
