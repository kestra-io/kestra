import {describe, expect, it, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const {generate} = vi.hoisted(() => ({generate: vi.fn()}))

vi.mock("../../../../src/components/dashboard/composables/useDashboards", () => ({
    useChartGenerator: () => ({EMPTY_TEXT: "", generate}),
    isPaginationEnabled: () => true,
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

import Table from "../../../../src/components/dashboard/sections/Table.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false})

const EXECUTIONS = "io.kestra.plugin.core.dashboard.data.Executions"

const ALL_STATES = [
    "SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING", "KILLING",
    "PAUSED", "BREAKPOINT",
    "SUCCESS",
    "WARNING",
    "FAILED", "KILLED", "CANCELLED", "SKIPPED", "RETRIED",
]

const mountTable = (where?: unknown) =>
    mount(Table, {
        props: {
            dashboardId: "d1",
            chart: {
                id: "executions",
                type: "io.kestra.plugin.core.dashboard.chart.Table",
                data: {type: EXECUTIONS, columns: {state: {field: "STATE"}}, ...(where ? {where} : {})},
            },
        },
        global: {
            plugins: [i18n],
            stubs: {KsDataTable: true, KsTableColumn: true, KsTableEmpty: true, TableQuickFilter: true, Motion: true},
        },
    })

describe("Table initial quick-filter state", () => {
    beforeEach(() => {
        generate.mockReset()
        generate.mockResolvedValue({results: [], total: 0})
    })

    it("seeds the All-tab override on first load when the chart constrains STATE", async () => {
        mountTable([{type: "IN", field: "STATE", values: ["RUNNING"]}])
        await flushPromises()

        expect(generate).toHaveBeenCalled()
        expect(generate.mock.calls[0][2]).toEqual([
            {field: "state", operation: "IN", value: ALL_STATES},
        ])
    })

    it("sends no state override on first load when the chart does not constrain STATE", async () => {
        mountTable()
        await flushPromises()

        expect(generate).toHaveBeenCalled()
        expect(generate.mock.calls[0][2]).toBeUndefined()
    })
})
