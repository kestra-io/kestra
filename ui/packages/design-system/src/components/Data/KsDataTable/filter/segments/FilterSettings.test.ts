import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {computed, h, nextTick, ref} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import FilterSettings from "./FilterSettings.vue"
import {FILTER_CONTEXT_INJECTION_KEY, type FilterContext, type TableOptions} from "../utils/filterInjectionKeys"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

function makeContext(chartVisibleRef: ReturnType<typeof ref<boolean>>, tableOptionsRef: ReturnType<typeof ref<TableOptions>>, updateChart: ReturnType<typeof vi.fn>): FilterContext {
    return {
        groups: computed(() => []),
        readOnly: computed(() => false),
        configuration: computed(() => ({title: "", keys: []})),
        topLogical: computed(() => "AND" as const),
        appliedFilters: computed(() => []),
        hasUnrenderableFilters: computed(() => false),
        rawQuery: computed(() => ""),
        viewMode: ref("chip" as const),
        searchQuery: ref(""),
        editingFilter: ref(undefined),
        chartVisible: computed(() => chartVisibleRef.value),
        hasFilterKeys: computed(() => false),
        showSearchInput: computed(() => false),
        hasAppliedFilters: computed(() => false),
        hasDismissedDefaultVisibleKeys: computed(() => false),
        tableOptions: computed(() => tableOptionsRef.value),
        savedFilters: computed(() => []),
        properties: computed(() => ({shown: false})),
        searchInputFullWidth: computed(() => false),
        buttons: computed(() => ({
            savedFilters: {shown: false},
            tableOptions: {shown: false},
        })),
        clearFilters: vi.fn(),
        addFilter: vi.fn(),
        updateFilter: vi.fn(),
        removeFilter: vi.fn(),
        moveFilter: vi.fn(),
        placeFilter: vi.fn(),
        addGroup: vi.fn(),
        removeGroup: vi.fn(),
        replaceTree: vi.fn(),
        setTopLogical: vi.fn(),
        setWrapperLogical: vi.fn(),
        wrapGroups: vi.fn(),
        unwrapGroup: vi.fn(),
        refreshData: vi.fn(),
        closeEditFilter: vi.fn(),
        updateChart,
        applyRawQuery: vi.fn(),
        setViewMode: vi.fn(),
        loadSavedFilter: vi.fn(),
        editSavedFilter: vi.fn(),
        updateProperties: vi.fn(),
        deleteSavedFilter: vi.fn(),
        resetToDefaults: vi.fn(),
        getDefaultValue: vi.fn(() => undefined),
        hasPreApplied: vi.fn(() => false),
        getPreApplied: vi.fn(() => undefined),
        updateSavedFilter: vi.fn(),
        saveFilter: vi.fn(),
    }
}

async function mountFilterSettings(chartVisible = true, tableOptions: TableOptions = {chart: {shown: true}}, updateChart = vi.fn()) {
    const chartVisibleRef = ref(chartVisible)
    const tableOptionsRef = ref(tableOptions)
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/logs", name: "logs", component: {render: () => null}}],
    })
    await router.push({name: "logs"})
    await router.isReady()

    const wrapper = i18nMount(FilterSettings, {
        global: {
            plugins: [router],
            provide: {
                [FILTER_CONTEXT_INJECTION_KEY as symbol]: makeContext(chartVisibleRef, tableOptionsRef, updateChart),
            },
        },
        slots: {
            "extra-options": () => h("div", {class: "extra-option"}, "Extra option"),
        },
    })
    return {wrapper, chartVisibleRef, tableOptionsRef, updateChart}
}

beforeEach(() => {
    localStorage.clear()
})

afterEach(() => {
    document.body.innerHTML = ""
    localStorage.clear()
})

describe("FilterSettings chart row", () => {
    test("renders the chart row when the table options allow a chart", async () => {
        const {wrapper} = await mountFilterSettings(true, {chart: {shown: true}})

        expect(wrapper.text()).toContain("filter.show chart")
    })

    test("hides the chart row when chart.shown is false", async () => {
        const {wrapper} = await mountFilterSettings(true, {chart: {shown: false}})

        expect(wrapper.text()).not.toContain("filter.show chart")
        // Only the auto-refresh switch remains.
        expect(wrapper.findAll(".kel-switch")).toHaveLength(1)
    })

    test("shows the chart row when no chart option is declared", async () => {
        const {wrapper} = await mountFilterSettings(true, {})

        expect(wrapper.text()).toContain("filter.show chart")
    })
})

describe("FilterSettings chart switch", () => {
    test("starts at the context's chartVisible value", async () => {
        const visible = await mountFilterSettings(true, {chart: {shown: true}})
        expect(visible.wrapper.findAll(".kel-switch")[0].classes()).toContain("is-checked")

        const hidden = await mountFilterSettings(false, {chart: {shown: true}})
        expect(hidden.wrapper.findAll(".kel-switch")[0].classes()).not.toContain("is-checked")
    })

    test("a context change updates the switch, and undefined is treated as visible", async () => {
        const {wrapper, chartVisibleRef} = await mountFilterSettings(true, {chart: {shown: true}})
        const chartSwitch = () => wrapper.findAll(".kel-switch")[0]

        expect(chartSwitch().classes()).toContain("is-checked")

        chartVisibleRef.value = false
        await nextTick()
        expect(chartSwitch().classes()).not.toContain("is-checked")

        chartVisibleRef.value = undefined as unknown as boolean
        await nextTick()
        expect(chartSwitch().classes()).toContain("is-checked")
    })

    test("flipping the switch calls updateChart with the new value", async () => {
        const {wrapper, updateChart} = await mountFilterSettings(true, {chart: {shown: true}})

        expect(updateChart).not.toHaveBeenCalled()

        await wrapper.findAll(".kel-switch")[0].trigger("click")
        await nextTick()

        expect(updateChart).toHaveBeenCalledTimes(1)
        expect(updateChart).toHaveBeenCalledWith(false)
    })
})

describe("FilterSettings panel", () => {
    test("always renders the auto-refresh row", async () => {
        const withChart = await mountFilterSettings(true, {chart: {shown: true}})
        expect(withChart.wrapper.text()).toContain("filter.auto refresh")

        const withoutChart = await mountFilterSettings(true, {chart: {shown: false}})
        expect(withoutChart.wrapper.text()).toContain("filter.auto refresh")
    })

    test("renders the extra-options slot", async () => {
        const {wrapper} = await mountFilterSettings()

        expect(wrapper.find(".extra-option").exists()).toBe(true)
        expect(wrapper.find(".extra-option").text()).toBe("Extra option")
    })

    test("the close button emits close", async () => {
        const {wrapper} = await mountFilterSettings()

        const closeButton = wrapper.find("button[aria-label=\"filter.close\"]")
        expect(closeButton.exists()).toBe(true)

        await closeButton.trigger("click")

        expect(wrapper.emitted("close")).toHaveLength(1)
    })
})
