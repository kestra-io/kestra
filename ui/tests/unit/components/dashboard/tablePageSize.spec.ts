import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../i18nMount"

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

const mountTable = () =>
    i18nMount(Table, {
        props: {
            dashboardId: "d1",
            chart: {
                id: "executions",
                type: "io.kestra.plugin.core.dashboard.chart.Table",
                data: {type: "io.kestra.plugin.core.dashboard.data.Executions", columns: {state: {field: "STATE"}}},
            },
        },
        global: {
            stubs: {KsDataTable: true, KsTableColumn: true, KsNoData: true, TableQuickFilter: true, Motion: true},
        },
    })

describe("Table page size persistence", () => {
    beforeEach(() => {
        generate.mockReset()
        generate.mockResolvedValue({results: [{state: "SUCCESS"}], total: 16})
    })

    afterEach(() => sessionStorage.clear())

    it("loads the page size the user last chose, back on the first page", async () => {
        const first = mountTable()
        await flushPromises()

        first.findComponent({name: "KsDataTable"}).vm.$emit("page-changed", {page: 2, size: 10})
        await flushPromises()
        first.unmount()

        mountTable()
        await flushPromises()

        expect(generate).toHaveBeenLastCalledWith({pageNumber: 1, pageSize: 10}, undefined, undefined)
    })
})
