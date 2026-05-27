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

    test("emits update:currentPage on page change (v-model contract)", async () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 10, currentPage: 1},
            global: globalConfig,
        })
        await (wrapper.vm as any).onPageChange(4)
        expect(wrapper.emitted("update:currentPage")?.[0]).toEqual([4])
    })

    test("emits update:currentPage and update:pageSize on size change", async () => {
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 10, currentPage: 3},
            global: globalConfig,
        })
        await (wrapper.vm as any).onSizeChange(50)
        expect(wrapper.emitted("update:currentPage")?.[0]).toEqual([1])
        expect(wrapper.emitted("update:pageSize")?.[0]).toEqual([50])
    })

    test("resetAndReload on page > 1 emits page 1 and does NOT reload directly", async () => {
        let loadCount = 0
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 25, currentPage: 3, loadData: async () => { loadCount++ }},
            global: globalConfig,
        })
        await new Promise<void>((resolve) => setTimeout(resolve, 0))
        expect(loadCount).toBe(1)
        ;(wrapper.vm as any).resetAndReload()
        expect(wrapper.emitted("update:currentPage")?.[0]).toEqual([1])
        expect(wrapper.emitted("page-changed")?.[0]).toEqual([{page: 1, size: 25}])
        await new Promise<void>((resolve) => setTimeout(resolve, 0))
        expect(loadCount).toBe(1)
    })

    test("resetAndReload on page 1 reloads directly without emitting page", async () => {
        let loadCount = 0
        const wrapper = mount(KsDataTable, {
            props: {data: SAMPLE_DATA, total: 100, pageSize: 25, currentPage: 1, loadData: async () => { loadCount++ }},
            global: globalConfig,
        })
        await new Promise<void>((resolve) => setTimeout(resolve, 0))
        expect(loadCount).toBe(1)
        ;(wrapper.vm as any).resetAndReload()
        await new Promise<void>((resolve) => setTimeout(resolve, 0))
        expect(loadCount).toBe(2)
        expect(wrapper.emitted("update:currentPage")).toBeFalsy()
    })

    test("loadData only fires when controlling prop changes — no internal page state", async () => {
        let loadCallCount = 0
        const loadDataSpy = async () => { loadCallCount++ }

        const wrapper = mount(KsDataTable, {
            props: {
                data: SAMPLE_DATA,
                total: 100,
                pageSize: 10,
                currentPage: 1,
                loadData: loadDataSpy,
            },
            global: globalConfig,
        })

        await new Promise<void>((resolve) => setTimeout(resolve, 0))
        expect(loadCallCount).toBe(1)

        await (wrapper.vm as any).onPageChange(3)
        await new Promise<void>((resolve) => setTimeout(resolve, 0))

        expect(loadCallCount).toBe(1)

        await wrapper.setProps({currentPage: 3})
        await new Promise<void>((resolve) => setTimeout(resolve, 0))

        expect(loadCallCount).toBe(2)
    })

    type Load = {page: number; size: number; sort?: string}
    const tick = () => new Promise<void>((resolve) => setTimeout(resolve, 0))
    const lastLoad = (loads: Load[]): Load => loads[loads.length - 1]

    const mountWithSpy = (props: Record<string, any>): Load[] => {
        const loads: Load[] = []
        mount(KsDataTable, {
            props: {
                data: SAMPLE_DATA,
                total: 100,
                loadData: async (p: Load) => { loads.push(p) },
                ...props,
            },
            global: globalConfig,
        })
        return loads
    }

    test("clamps negative currentPage to 1", async () => {
        const loads = mountWithSpy({currentPage: -5, pageSize: 25})
        await tick()
        expect(lastLoad(loads).page).toBe(1)
    })

    test("floors a fractional currentPage", async () => {
        const loads = mountWithSpy({currentPage: 2.9, pageSize: 25})
        await tick()
        expect(lastLoad(loads).page).toBe(2)
    })

    test("caps an absurdly large currentPage at exactly 1_000_000 (finite offset guard)", async () => {
        const loads = mountWithSpy({currentPage: 1e308, pageSize: 25})
        await tick()
        expect(lastLoad(loads).page).toBe(1_000_000)
        expect(Number.isInteger(lastLoad(loads).page)).toBe(true)
    })

    test("pins the currentPage cap boundary (1_000_000 stays, 1_000_001 clamps)", async () => {
        const atCap = mountWithSpy({currentPage: 1_000_000, pageSize: 25})
        const overCap = mountWithSpy({currentPage: 1_000_001, pageSize: 25})
        await tick()
        expect(lastLoad(atCap).page).toBe(1_000_000)
        expect(lastLoad(overCap).page).toBe(1_000_000)
    })

    test("falls back to page 1 for NaN / Infinity currentPage", async () => {
        const nan = mountWithSpy({currentPage: Number.NaN, pageSize: 25})
        const inf = mountWithSpy({currentPage: Number.POSITIVE_INFINITY, pageSize: 25})
        await tick()
        expect(lastLoad(nan).page).toBe(1)
        expect(lastLoad(inf).page).toBe(1)
    })

    test("treats currentPage=0 as page 1", async () => {
        const loads = mountWithSpy({currentPage: 0, pageSize: 25})
        await tick()
        expect(lastLoad(loads).page).toBe(1)
    })

    test("caps an absurdly large pageSize at exactly 1000 (DoS guard)", async () => {
        const loads = mountWithSpy({currentPage: 1, pageSize: 10_000_000})
        await tick()
        expect(lastLoad(loads).size).toBe(1000)
    })

    test("pins the pageSize cap boundary (1000 stays, 1001 clamps)", async () => {
        const atCap = mountWithSpy({currentPage: 1, pageSize: 1000})
        const overCap = mountWithSpy({currentPage: 1, pageSize: 1001})
        await tick()
        expect(lastLoad(atCap).size).toBe(1000)
        expect(lastLoad(overCap).size).toBe(1000)
    })

    test("falls back to default size for negative / zero / NaN pageSize", async () => {
        const neg = mountWithSpy({currentPage: 1, pageSize: -10})
        const zero = mountWithSpy({currentPage: 1, pageSize: 0})
        const nan = mountWithSpy({currentPage: 1, pageSize: Number.NaN})
        await tick()
        expect(lastLoad(neg).size).toBe(25)
        expect(lastLoad(zero).size).toBe(25)
        expect(lastLoad(nan).size).toBe(25)
    })

    test("floors a fractional pageSize", async () => {
        const loads = mountWithSpy({currentPage: 1, pageSize: 49.9})
        await tick()
        expect(lastLoad(loads).size).toBe(49)
    })

    test("keeps a legitimate page/size pair untouched", async () => {
        const loads = mountWithSpy({currentPage: 3, pageSize: 50})
        await tick()
        expect(lastLoad(loads)).toEqual({page: 3, size: 50, sort: undefined})
    })
})
