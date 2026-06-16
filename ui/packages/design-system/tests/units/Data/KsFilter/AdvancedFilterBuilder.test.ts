import {describe, test, expect, vi, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {computed, ref} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "../../../../src/index"
import AdvancedFilterBuilder from "../../../../src/components/Data/KsDataTable/filter/AdvancedFilterBuilder.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import type {FilterContext} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"

const makeCtx = (): FilterContext => ({
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
})

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem],
    provide: {[FILTER_CONTEXT_INJECTION_KEY as symbol]: makeCtx()},
}

afterEach(() => {
    document.body.innerHTML = ""
})

describe("AdvancedFilterBuilder", () => {
    test("panel has role=dialog when open", async () => {
        mount(AdvancedFilterBuilder, {
            props: {modelValue: true},
            global: globalConfig,
            attachTo: document.body,
        })
        const panel = document.body.querySelector(".adv-builder")
        expect(panel).not.toBeNull()
        expect(panel?.getAttribute("role")).toBe("dialog")
    })

    test("panel has aria-modal=true when open", async () => {
        mount(AdvancedFilterBuilder, {
            props: {modelValue: true},
            global: globalConfig,
            attachTo: document.body,
        })
        const panel = document.body.querySelector(".adv-builder")
        expect(panel?.getAttribute("aria-modal")).toBe("true")
    })

    test("panel has an accessible label when open", async () => {
        mount(AdvancedFilterBuilder, {
            props: {modelValue: true},
            global: globalConfig,
            attachTo: document.body,
        })
        const panel = document.body.querySelector(".adv-builder")
        expect(panel?.getAttribute("aria-label")).toBeTruthy()
    })

    test("Escape key closes the panel", async () => {
        const wrapper = mount(AdvancedFilterBuilder, {
            props: {modelValue: true},
            global: globalConfig,
            attachTo: document.body,
        })
        window.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted("update:modelValue")).toEqual([[false]])
    })

    test("adds resize listener to window when panel opens", async () => {
        const addSpy = vi.spyOn(window, "addEventListener")
        const wrapper = mount(AdvancedFilterBuilder, {
            props: {modelValue: false},
            global: globalConfig,
            attachTo: document.body,
        })
        await wrapper.setProps({modelValue: true})
        expect(addSpy).toHaveBeenCalledWith("resize", expect.any(Function))
        addSpy.mockRestore()
    })

    test("removes resize listener from window when panel closes", async () => {
        const removeSpy = vi.spyOn(window, "removeEventListener")
        const wrapper = mount(AdvancedFilterBuilder, {
            props: {modelValue: true},
            global: globalConfig,
            attachTo: document.body,
        })
        await wrapper.setProps({modelValue: false})
        expect(removeSpy).toHaveBeenCalledWith("resize", expect.any(Function))
        removeSpy.mockRestore()
    })
})
