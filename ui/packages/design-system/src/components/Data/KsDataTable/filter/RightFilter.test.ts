import {afterEach, describe, expect, test, vi} from "vitest"
import {computed, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import RightFilter from "./RightFilter.vue"
import {FILTER_CONTEXT_INJECTION_KEY, type FilterContext} from "./utils/filterInjectionKeys"
import {i18nMount} from "../../../../../tests/units/i18nMount"

function makeContext(refreshData: FilterContext["refreshData"]): FilterContext {
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
        chartVisible: computed(() => false),
        hasFilterKeys: computed(() => false),
        showSearchInput: computed(() => false),
        hasAppliedFilters: computed(() => false),
        hasDismissedDefaultVisibleKeys: computed(() => false),
        tableOptions: computed(() => ({
            columns: {shown: false},
            refresh: {shown: true},
        })),
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
        refreshData,
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
    }
}

function deferred() {
    let resolve!: () => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<void>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise
        reject = rejectPromise
    })
    return {promise, resolve, reject}
}

function mountRightFilter(refreshData: FilterContext["refreshData"]) {
    return i18nMount(RightFilter, {
        global: {
            provide: {
                [FILTER_CONTEXT_INJECTION_KEY as symbol]: makeContext(refreshData),
            },
        },
    })
}

afterEach(() => {
    document.body.innerHTML = ""
})

describe("RightFilter refresh", () => {
    test("showsAStableDisabledLoadingButtonAndIgnoresRepeatedClicksUntilSuccess", async () => {
        const pending = deferred()
        const refreshData = vi.fn(() => pending.promise)
        const wrapper = mountRightFilter(refreshData)
        const button = wrapper.get("button.refresh-button")
        const label = button.text()
        const style = button.attributes("style")

        expect(button.find(".refresh-icon").exists()).toBe(true)
        await button.trigger("click")

        expect(refreshData).toHaveBeenCalledTimes(1)
        expect(button.attributes("disabled")).toBeDefined()
        expect(button.find(".refresh-icon").exists()).toBe(false)
        expect(button.text()).toBe(label)
        expect(button.attributes("style")).toBe(style)

        await button.trigger("click")
        expect(refreshData).toHaveBeenCalledTimes(1)

        pending.resolve()
        await flushPromises()

        expect(button.classes()).not.toContain("is-loading")
        expect(button.attributes("disabled")).toBeUndefined()
        expect(button.find(".refresh-icon").exists()).toBe(true)
    })

    test("resetsLoadingAfterARejectedPromise", async () => {
        const pending = deferred()
        const wrapper = mountRightFilter(vi.fn(() => pending.promise))
        const button = wrapper.get("button.refresh-button")

        await button.trigger("click")
        expect(button.classes()).toContain("is-loading")

        pending.reject(new Error("Refresh failed"))
        await flushPromises()

        expect(button.classes()).not.toContain("is-loading")
        expect(button.attributes("disabled")).toBeUndefined()
    })

    test("resetsLoadingAfterASynchronousException", async () => {
        const refreshData = vi.fn(() => {
            throw new Error("Refresh failed")
        })
        const wrapper = mountRightFilter(refreshData)
        const button = wrapper.get("button.refresh-button")

        await button.trigger("click")

        expect(refreshData).toHaveBeenCalledTimes(1)
        expect(button.classes()).not.toContain("is-loading")
        expect(button.attributes("disabled")).toBeUndefined()
    })

    test("keepsSynchronousRefreshCallbacksUnchanged", async () => {
        const refreshData = vi.fn(() => undefined)
        const wrapper = mountRightFilter(refreshData)
        const button = wrapper.get("button.refresh-button")

        await button.trigger("click")

        expect(refreshData).toHaveBeenCalledTimes(1)
        expect(button.classes()).not.toContain("is-loading")
        expect(button.attributes("disabled")).toBeUndefined()
        expect(button.find(".refresh-icon").exists()).toBe(true)
    })
})
