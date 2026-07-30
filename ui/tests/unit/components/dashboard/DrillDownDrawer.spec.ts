import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import KsDataTable from "@kestra-io/design-system/components/Data/KsDataTable/KsDataTable.vue"

const mockPush = vi.fn()
vi.mock("vue-router", () => ({
    useRouter: () => ({push: mockPush}),
    useRoute: () => ({params: {tenant: "main"}}),
}))

import DrillDownDrawer from "../../../../src/components/dashboard/DrillDownDrawer.vue"
import {useDrillDownStore} from "../../../../src/stores/drillDown"
import {registerDrillDownPreview} from "../../../../src/components/dashboard/composables/drillDownPreview"
import en from "../../../../src/translations/en.json"

// Real messages (not an empty i18n instance): catches key collisions/typos that a bare $t()
// call can't surface any other way, e.g. a key silently shadowed by a same-named object elsewhere
// in en.json, which resolves to the raw key string instead of the translated text.
const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

// KsDrawer teleports to document.body and adds Element Plus transition/overlay machinery that's
// irrelevant here; a plain v-if stub keeps the assertions focused on DrillDownDrawer's own wiring.
// LogsWrapper is a heavy page-level component (its own stores, websocket, etc.) — stub it too and
// just assert on the props it receives, matching the SourceSearchPreview.spec.ts precedent for
// stubbing heavy subcomponents.
const stubs = {
    KsDrawer: {
        props: ["modelValue", "title"],
        template: "<div v-if=\"modelValue\" data-test=\"drawer\"><span data-test=\"drawer-title\">{{ title }}</span><slot /><slot name=\"footer\" /></div>",
    },
    LogsWrapper: {
        props: ["filters", "embed", "withCharts"],
        template: "<div data-test=\"logs-wrapper\" :data-filters=\"JSON.stringify(filters)\" :data-with-charts=\"withCharts\" />",
    },
}

function mountDrawer() {
    return mount(DrillDownDrawer, {
        global: {plugins: [i18n, KestraDesignSystem], stubs},
    })
}

describe("DrillDownDrawer", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        mockPush.mockClear()
    })

    test("renders nothing when the store is closed", () => {
        const wrapper = mountDrawer()
        expect(wrapper.find("[data-test='drawer']").exists()).toBe(false)
    })

    test("drawer title resolves to a real translated string, not the raw i18n key", async () => {
        registerDrillDownPreview("test/drawer-title/list", {mode: "logs"})

        const wrapper = mountDrawer()
        useDrillDownStore().open({name: "test/drawer-title/list", query: {}, timeFiltered: false})
        await flushPromises()

        expect(wrapper.find("[data-test='drawer-title']").text()).toBe("Preview")
    })

    test("mode: table — fetches with commit:false, renders rows; row double-click navigates + closes", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            results: [{id: "e1", namespace: "ns", flowId: "f1"}],
            total: 1,
        })
        const rowDetail = vi.fn().mockReturnValue({name: "executions/update", params: {tenant: "main", id: "e1"}})
        registerDrillDownPreview("test/drawer-table/list", {
            mode: "table",
            columns: [{prop: "id", label: "Id"}],
            fetch: fetchMock,
            rowDetail,
        })

        const wrapper = mountDrawer()
        const store = useDrillDownStore()
        store.open({name: "test/drawer-table/list", query: {"filters[x][EQUALS]": "v"}, timeFiltered: false})
        await flushPromises()

        expect(fetchMock).toHaveBeenCalledWith(expect.objectContaining({
            "filters[x][EQUALS]": "v",
            scope: "USER",
            page: 1,
            size: 25,
        }))

        const dataTable = wrapper.findComponent(KsDataTable)
        expect(dataTable.props("data")).toEqual([{id: "e1", namespace: "ns", flowId: "f1"}])
        expect(dataTable.props("total")).toBe(1)

        await dataTable.vm.$emit("row-dblclick", {id: "e1", namespace: "ns", flowId: "f1"})

        expect(rowDetail).toHaveBeenCalledWith({id: "e1", namespace: "ns", flowId: "f1"}, "main")
        expect(mockPush).toHaveBeenCalledWith({name: "executions/update", params: {tenant: "main", id: "e1"}})
        expect(store.isOpen).toBe(false)
    })

    test("mode: table — page-changed re-fetches the next page", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            results: [{id: "e1", namespace: "ns", flowId: "f1"}],
            total: 30,
        })
        registerDrillDownPreview("test/drawer-pagination/list", {
            mode: "table",
            columns: [{prop: "id", label: "Id"}],
            fetch: fetchMock,
            rowDetail: vi.fn(),
        })

        const wrapper = mountDrawer()
        useDrillDownStore().open({name: "test/drawer-pagination/list", query: {}, timeFiltered: false})
        await flushPromises()

        const dataTable = wrapper.findComponent(KsDataTable)
        expect(fetchMock).toHaveBeenLastCalledWith(expect.objectContaining({page: 1, size: 25}))

        await dataTable.vm.$emit("page-changed", {page: 2, size: 25})
        await flushPromises()

        expect(fetchMock).toHaveBeenLastCalledWith(expect.objectContaining({page: 2, size: 25}))
    })

    test("mode: logs — renders LogsWrapper with the encoded filters (no size/page)", async () => {
        registerDrillDownPreview("test/drawer-logs/list", {mode: "logs"})

        const wrapper = mountDrawer()
        const store = useDrillDownStore()
        store.open({name: "test/drawer-logs/list", query: {"filters[taskId][EQUALS]": "t"}, timeFiltered: false})
        await flushPromises()

        const logsWrapper = wrapper.find("[data-test='logs-wrapper']")
        expect(logsWrapper.exists()).toBe(true)

        const filters = JSON.parse(logsWrapper.attributes("data-filters")!)
        expect(filters).toEqual({"filters[taskId][EQUALS]": "t", scope: "USER"})
        expect(filters.size).toBeUndefined()
        expect(filters.page).toBeUndefined()

        expect(logsWrapper.attributes("data-with-charts")).toBe("false")
    })

    test("Open full page pushes the exact legacy query and closes the drawer", async () => {
        registerDrillDownPreview("test/drawer-openfull/list", {mode: "logs"})

        const wrapper = mountDrawer()
        const store = useDrillDownStore()
        store.open({name: "test/drawer-openfull/list", query: {"filters[x][EQUALS]": "v"}, timeFiltered: true})
        await flushPromises()

        await wrapper.find("button").trigger("click")

        expect(mockPush).toHaveBeenCalledWith({
            name: "test/drawer-openfull/list",
            params: {tenant: "main"},
            query: {
                "filters[x][EQUALS]": "v",
                scope: "USER",
                size: 100,
                page: 1,
                "filters[timeRange][EQUALS]": "PT24H",
            },
        })
        expect(store.isOpen).toBe(false)
    })
})
