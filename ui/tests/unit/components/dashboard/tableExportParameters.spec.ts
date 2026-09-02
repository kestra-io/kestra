import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
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

const mountTable = () =>
    mount(Table, {
        props: {
            dashboardId: "d1",
            chart: {
                id: "executions",
                type: "io.kestra.plugin.core.dashboard.chart.Table",
                data: {type: "io.kestra.plugin.core.dashboard.data.Executions", columns: {state: {field: "STATE"}}},
            },
        },
        global: {
            plugins: [i18n],
            stubs: {KsDataTable: true, KsTableColumn: true, KsNoData: true, TableQuickFilter: true, Motion: true},
        },
    })

describe("Table export parameters", () => {
    beforeEach(() => {
        generate.mockReset()
        generate.mockResolvedValue({results: [{state: "SUCCESS"}], total: 16})
    })

    afterEach(() => sessionStorage.clear())

    it("reports the page and the quick filter the table is currently showing", async () => {
        const wrapper = mountTable()
        await flushPromises()

        wrapper.findComponent({name: "TableQuickFilter"}).vm.$emit(
            "change",
            {field: "state", operation: "IN", value: ["FAILED"]},
            "failed",
        )
        await flushPromises()

        wrapper.findComponent({name: "KsDataTable"}).vm.$emit("page-changed", {page: 2, size: 10})
        await flushPromises()

        expect(wrapper.vm.exportParameters()).toEqual({
            pageNumber: 2,
            pageSize: 10,
            filters: [{field: "state", operation: "IN", value: ["FAILED"]}],
        })
    })
})
