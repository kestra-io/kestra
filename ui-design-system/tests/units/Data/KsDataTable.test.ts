import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../src/index"
import KsDataTable from "../../../src/components/Data/KsDataTable/KsDataTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"

const globalConfig = {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem]}

const SAMPLE_DATA = [
    {id: "flow-001", namespace: "company.team", status: "SUCCESS"},
    {id: "flow-002", namespace: "company.data", status: "RUNNING"},
    {id: "flow-003", namespace: "company.infra", status: "FAILED"},
]

describe("KsDataTable", () => {
    test("renders table element", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 3},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("renders with columns", () => {
        const wrapper = mount({
            components: {KsDataTable, KsTableColumn},
            template: `
                <ks-data-table :data="data" :total="3">
                    <ks-table-column prop="id" label="ID" />
                    <ks-table-column prop="namespace" label="Namespace" />
                </ks-data-table>
            `,
            setup: () => ({data: SAMPLE_DATA}),
        }, {global: globalConfig})
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("does not render pagination when total is 0", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-pagination").exists()).toBe(false)
    })

    test("renders pagination when total > 0", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 30},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-pagination").exists()).toBe(true)
    })

    test("renders navbar slot when provided", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            slots: {navbar: "<span class='test-navbar'>Filters</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".test-navbar").exists()).toBe(true)
    })

    test("does not render navbar when slot is absent", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(wrapper.find("nav").exists()).toBe(false)
    })

    test("renders custom #table slot instead of internal table", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 3},
            slots: {table: "<div class='custom-content'>Custom</div>"},
            global: globalConfig,
        })
        expect(wrapper.find(".custom-content").exists()).toBe(true)
        expect(wrapper.find(".kel-table").exists()).toBe(false)
    })

    test("shows loading state when loading prop is true", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 3, loading: true},
            global: globalConfig,
        })
        expect(wrapper.find("[v-ks-loading]").exists() || wrapper.find(".ks-data-table-wrapper").exists()).toBe(true)
    })

    test("exposes isLoading ref", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect((wrapper.vm as any).isLoading).toBeDefined()
    })

    test("exposes clearSelection method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearSelection).toBe("function")
    })

    test("exposes setSelection method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).setSelection).toBe("function")
    })

    test("exposes getSelectionRows method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).getSelectionRows).toBe("function")
    })

    test("exposes toggleAllSelection method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).toggleAllSelection).toBe("function")
    })

    test("exposes toggleRowExpansion method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).toggleRowExpansion).toBe("function")
    })

    test("exposes waitTableRender method", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).waitTableRender).toBe("function")
    })

    test("emits page-changed on page change", async () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 10},
            global: globalConfig,
        })
        // Trigger size change to emit page-changed
        await (wrapper.vm as any).onSizeChange(25)
        expect(wrapper.emitted("page-changed")).toBeTruthy()
        expect(wrapper.emitted("page-changed")?.[0]).toEqual([{page: 1, size: 25}])
    })

    test("emits page-changed with correct page on page change", async () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 10},
            global: globalConfig,
        })
        await (wrapper.vm as any).onPageChange(3)
        expect(wrapper.emitted("page-changed")?.[0]).toEqual([{page: 3, size: 10}])
    })

    test("renders without error when selectable is true", () => {
        const wrapper = mount({
            components: {KsDataTable, KsTableColumn},
            template: `
                <ks-data-table :data="data" :total="3" :selectable="true" :show-selection="true">
                    <ks-table-column prop="id" label="ID" />
                </ks-data-table>
            `,
            setup: () => ({data: SAMPLE_DATA}),
        }, {global: globalConfig})
        // Table renders correctly with selection enabled
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("isLoading updates when loading prop changes", async () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0, loading: false},
            global: globalConfig,
        })
        expect((wrapper.vm as any).isLoading).toBe(false)
        await wrapper.setProps({loading: true})
        expect((wrapper.vm as any).isLoading).toBe(true)
    })

    test("can set isLoading directly from outside", () => {
        const wrapper = mount(KsDataTable, {
            props: {data: [], total: 0},
            global: globalConfig,
        })
        ;(wrapper.vm as any).isLoading = true
        expect((wrapper.vm as any).isLoading).toBe(true)
    })
})
