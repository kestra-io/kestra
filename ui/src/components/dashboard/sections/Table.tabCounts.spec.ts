import {describe, expect, it, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {generate} = vi.hoisted(() => ({generate: vi.fn()}))

vi.mock("../composables/useDashboards", () => ({
    useChartGenerator: () => ({EMPTY_TEXT: "", generate}),
    isPaginationEnabled: () => true,
    CHART_RESULTS_KEY: Symbol("chartResults"),
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

const stateOf = (call: unknown[]) => (call[2] as {value: string[]}[] | undefined)?.[0]?.value

describe("Table quick filter counts", () => {
    beforeEach(() => {
        generate.mockReset()
        generate.mockImplementation(async (_pagination, _custom, append?: {value: string[]}[]) => {
            const states = append?.[0]?.value
            if (!states) return {results: [{state: "SUCCESS"}], total: 12}
            return {results: [], total: states.includes("SUCCESS") ? 9 : 0}
        })
    })

    it("asks for one row per tab and hands the totals to the tabs", async () => {
        const wrapper = mountTable()
        await flushPromises()

        const countCalls = generate.mock.calls.filter(([pagination]) => pagination?.pageSize === 1)
        expect(countCalls).toHaveLength(6)
        expect(countCalls.every(([pagination]) => pagination.pageNumber === 1)).toBe(true)
        expect(countCalls.map(stateOf).filter(Boolean)).toContainEqual(["SUCCESS"])

        expect(wrapper.findComponent({name: "TableQuickFilter"}).props("counts")).toEqual({
            all: 12, running: 0, paused: 0, success: 9, warning: 0, failed: 0,
        })
    })

    it("does not request counts for a chart without quick filters", async () => {
        const wrapper = mountTable("io.kestra.plugin.core.dashboard.data.Logs")
        await flushPromises()

        expect(generate.mock.calls.filter(([pagination]) => pagination?.pageSize === 1)).toHaveLength(0)
        expect(wrapper.findComponent({name: "TableQuickFilter"}).props("counts")).toEqual({})
    })

    it("leaves the tabs without counts when a count request fails", async () => {
        generate.mockImplementation(async (pagination: {pageSize: number}) => {
            if (pagination?.pageSize === 1) throw new Error("boom")
            return {results: [], total: 0}
        })

        const wrapper = mountTable()
        await flushPromises()

        expect(wrapper.findComponent({name: "TableQuickFilter"}).props("counts")).toEqual({})
    })
})
