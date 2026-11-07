import {afterEach, describe, expect, test, vi} from "vitest"
import {computed, nextTick, ref} from "vue"
import RawFilter from "./RawFilter.vue"
import {FILTER_CONTEXT_INJECTION_KEY, type FilterContext} from "./utils/filterInjectionKeys"
import {i18nMount} from "../../../../../tests/units/i18nMount"

const RAW_QUERY = "filters[state][EQUALS]=FAILED&filters[namespace][EQUALS]=company"
const NEXT_QUERY = "filters[state][IN]=RUNNING&filters[flowId][EQUALS]=full"

const makeCtx = (over: Partial<FilterContext> = {}): FilterContext => ({
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
    updateChart: vi.fn(),
    applyRawQuery: vi.fn(),
    setViewMode: vi.fn(),
    loadSavedFilter: vi.fn(),
    editSavedFilter: vi.fn(),
    updateProperties: vi.fn(),
    deleteSavedFilter: vi.fn(),
    resetToDefaults: vi.fn(),
    getDefaultValue: vi.fn(() => ""),
    hasPreApplied: vi.fn(() => false),
    getPreApplied: vi.fn(() => undefined),
    updateSavedFilter: vi.fn(),
    saveFilter: vi.fn(),
    ...over,
})

const mountRawFilter = (ctx: FilterContext) =>
    i18nMount(RawFilter, {
        global: {
            provide: {[FILTER_CONTEXT_INJECTION_KEY as symbol]: ctx},
        },
    })

const mountWithRawQuery = (initial?: string) => {
    const rawQuery = ref<string | undefined>(initial)
    // The context type is non-nullable; issue #22297 still requires covering the undefined guard.
    const ctx = makeCtx({rawQuery: computed(() => rawQuery.value as string)})
    return {wrapper: mountRawFilter(ctx), rawQuery, ctx}
}

const draftValue = (wrapper: ReturnType<typeof mountRawFilter>) =>
    (wrapper.get(".raw-input").element as HTMLInputElement).value

afterEach(() => {
    document.body.innerHTML = ""
})

describe("RawFilter", () => {
    test("starts the draft from the context query, and empty when it is undefined", () => {
        const withQuery = mountWithRawQuery(RAW_QUERY).wrapper
        const withUndefined = mountWithRawQuery().wrapper

        expect(draftValue(withQuery)).toBe(RAW_QUERY)
        expect(draftValue(withUndefined)).toBe("")
        expect(withUndefined.get(".raw-apply").attributes("disabled")).toBeDefined()
    })

    test("is clean initially and dirty once the draft diverges", async () => {
        const {wrapper} = mountWithRawQuery(RAW_QUERY)

        expect(wrapper.get(".raw-apply").attributes("disabled")).toBeDefined()

        await wrapper.get(".raw-input").setValue("edited")

        expect(wrapper.get(".raw-apply").attributes("disabled")).toBeUndefined()
    })

    test("applying hands the current draft to applyRawQuery", async () => {
        const {wrapper, ctx} = mountWithRawQuery(RAW_QUERY)

        await wrapper.get(".raw-input").setValue(NEXT_QUERY)
        await wrapper.get(".raw-apply").trigger("click")

        expect(ctx.applyRawQuery).toHaveBeenCalledTimes(1)
        expect(ctx.applyRawQuery).toHaveBeenCalledWith(NEXT_QUERY)
    })

    test("reverting restores the context query and clears the dirty state", async () => {
        const {wrapper} = mountWithRawQuery(RAW_QUERY)

        await wrapper.get(".raw-input").setValue("edited")
        await wrapper.get(".raw-clear").trigger("click")

        expect(draftValue(wrapper)).toBe(RAW_QUERY)
        expect(wrapper.get(".raw-apply").attributes("disabled")).toBeDefined()
    })

    test("an external query change overwrites an untouched draft", async () => {
        const {wrapper, rawQuery} = mountWithRawQuery(RAW_QUERY)

        rawQuery.value = NEXT_QUERY
        await nextTick()

        expect(draftValue(wrapper)).toBe(NEXT_QUERY)
        expect(wrapper.get(".raw-apply").attributes("disabled")).toBeDefined()
    })

    test("an external query change leaves an edited draft alone", async () => {
        const {wrapper, rawQuery} = mountWithRawQuery(RAW_QUERY)

        await wrapper.get(".raw-input").setValue("local edit")
        rawQuery.value = NEXT_QUERY
        await nextTick()

        expect(draftValue(wrapper)).toBe("local edit")
        expect(wrapper.get(".raw-apply").attributes("disabled")).toBeUndefined()
    })

    test("an undefined incoming query is ignored", async () => {
        const {wrapper, rawQuery} = mountWithRawQuery(RAW_QUERY)

        rawQuery.value = undefined
        await nextTick()

        expect(draftValue(wrapper)).toBe(RAW_QUERY)
    })
})
