import {describe, test, expect, vi, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {computed, ref} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import MobileFilter from "../../../../src/components/Data/KsDataTable/filter/MobileFilter.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import type {FilterContext} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import type {AppliedFilter, FilterKeyConfig} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"

const KEYS: FilterKeyConfig[] = [
    {key: "namespace", label: "Namespace", valueType: "text", comparators: ["*=", "^="]},
    {key: "flowId", label: "Flow ID", valueType: "text", comparators: ["="]},
    {key: "labels", label: "Labels", valueType: "text", comparators: ["="]},
] as unknown as FilterKeyConfig[]

const makeCtx = (over: Partial<FilterContext> = {}): FilterContext => ({
    groups: computed(() => []),
    readOnly: computed(() => false),
    configuration: computed(() => ({title: "", keys: KEYS})),
    topLogical: computed(() => "AND" as const),
    appliedFilters: computed(() => []),
    hasUnrenderableFilters: computed(() => false),
    rawQuery: computed(() => ""),
    viewMode: ref("chip" as const),
    searchQuery: ref(""),
    editingFilter: ref(undefined),
    chartVisible: computed(() => false),
    hasFilterKeys: computed(() => true),
    showSearchInput: computed(() => true),
    hasAppliedFilters: computed(() => false),
    hasDismissedDefaultVisibleKeys: computed(() => false),
    tableOptions: computed(() => ({})),
    savedFilters: computed(() => []),
    properties: computed(() => ({shown: false})),
    searchInputFullWidth: computed(() => false),
    buttons: computed(() => ({})),
    clearFilters: vi.fn(),
    addFilter: vi.fn(),
    updateFilter: vi.fn(),
    removeFilter: vi.fn(),
    moveFilter: vi.fn(),
    addGroup: vi.fn(),
    removeGroup: vi.fn(),
    setTopLogical: vi.fn(),
    setWrapperLogical: vi.fn(),
    wrapGroups: vi.fn(),
    unwrapGroup: vi.fn(),
    refreshData: vi.fn(),
    closeEditFilter: vi.fn(),
    updateChart: vi.fn(),
    applyRawQuery: vi.fn(),
    setViewMode: vi.fn(),
    loadSavedFilter: vi.fn(),
    editSavedFilter: vi.fn(),
    updateProperties: vi.fn(),
    deleteSavedFilter: vi.fn(),
    resetToDefaults: vi.fn(),
    hasPreApplied: vi.fn(() => false),
    getPreApplied: vi.fn(() => undefined),
    updateSavedFilter: vi.fn(),
    saveFilter: vi.fn(),
    ...over,
})

const mountWith = (ctx: FilterContext) =>
    mount(MobileFilter, {
        global: {
            plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem],
            provide: {[FILTER_CONTEXT_INJECTION_KEY as symbol]: ctx},
        },
    })

afterEach(() => {
    document.body.innerHTML = ""
})

describe("MobileFilter", () => {
    test("renders the search input and filter toggle", () => {
        const wrapper = mountWith(makeCtx())
        expect(wrapper.find(".mobile-search").exists()).toBe(true)
        expect(wrapper.find(".mobile-toggle").exists()).toBe(true)
        expect(wrapper.find(".mobile-sheet").exists()).toBe(false)
    })

    test("opening the sheet exposes a labelled dialog", async () => {
        const wrapper = mountWith(makeCtx())
        await wrapper.find(".mobile-toggle").trigger("click")
        const sheet = wrapper.find(".mobile-sheet")
        expect(sheet.exists()).toBe(true)
        expect(sheet.attributes("role")).toBe("dialog")
        expect(sheet.attributes("aria-modal")).toBe("true")
        expect(sheet.attributes("aria-label")).toBeTruthy()
    })

    test("renders one field row per configured key", async () => {
        const wrapper = mountWith(makeCtx())
        await wrapper.find(".mobile-toggle").trigger("click")
        expect(wrapper.findAll(".field-row")).toHaveLength(KEYS.length)
    })

    test("shows an active count badge when filters are applied", async () => {
        const applied: AppliedFilter[] = [
            {id: "a", key: "namespace", keyLabel: "Namespace", comparator: "*=", comparatorLabel: "contains", value: "io.kestra", valueLabel: "io.kestra"},
        ] as unknown as AppliedFilter[]
        const wrapper = mountWith(makeCtx({
            appliedFilters: computed(() => applied),
            hasAppliedFilters: computed(() => true),
        }))
        expect(wrapper.find(".mobile-toggle-count").text()).toBe("1")
    })

    test("expanding a field row mounts the value editor", async () => {
        const wrapper = mountWith(makeCtx())
        await wrapper.find(".mobile-toggle").trigger("click")
        await wrapper.findAll(".field-row-header")[0].trigger("click")
        expect(wrapper.find(".field-row-body").exists()).toBe(true)
        expect(wrapper.find(".edit-popper").exists()).toBe(true)
    })

    test("Escape closes the sheet", async () => {
        const wrapper = mountWith(makeCtx())
        await wrapper.find(".mobile-toggle").trigger("click")
        expect(wrapper.find(".mobile-sheet").exists()).toBe(true)
        window.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
        await wrapper.vm.$nextTick()
        expect(wrapper.find(".mobile-sheet").exists()).toBe(false)
    })

    test("clear all triggers clearFilters", async () => {
        const ctx = makeCtx({hasAppliedFilters: computed(() => true)})
        const wrapper = mountWith(ctx)
        await wrapper.find(".mobile-toggle").trigger("click")
        await wrapper.find(".sheet-clear").trigger("click")
        expect(ctx.clearFilters).toHaveBeenCalled()
    })
})
