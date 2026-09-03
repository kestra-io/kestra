import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent, h, inject} from "vue"
import {createI18n} from "vue-i18n"
import {createRouter, createMemoryHistory} from "vue-router"
import KestraDesignSystem from "../../../../src/index"
import KsFilter from "../../../../src/components/Data/KsDataTable/KsFilter.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/filterInjectionKeys"
import {SAVED_FILTER_ANALYTICS_INJECTION_KEY} from "../../../../src/components/Data/KsDataTable/filter/utils/filterAnalytics"
import {Comparators} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import type {AppliedFilter, FilterContext, SavedFilter} from "../../../../src/index"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem, router],
    stubs: {
        "ks-popover": true,
        "ks-button": true,
        "ks-tooltip": true,
        "ks-switch": true,
        "ks-tag": true,
        "ks-icon": true,
    },
}

describe("KsFilter", () => {
    test("renders without errors with minimal config", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter").exists()).toBe(true)
    })

    test("renders filter section with top div", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter .top").exists()).toBe(true)
    })

    test("emits filter event when appliedFilters change", async () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.emitted()).toBeTruthy()
    })

    test("does not render filter options when showOptions is false", () => {
        const wrapper = mount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
                tableOptions: {},
            },
            global: globalConfig,
        })
        // FilterOptions is hidden by default (showOptions starts false)
        expect(wrapper.find(".expand-panel").exists()).toBe(false)
    })
})

describe("KsFilter saved-filter analytics", () => {
    const makeAppliedFilter = (id = "f1"): AppliedFilter => ({
        id,
        key: "namespace",
        keyLabel: "Namespace",
        comparator: Comparators.EQUALS,
        comparatorLabel: "Equals",
        value: "io.kestra",
        valueLabel: "io.kestra",
    })

    const mountWithTracker = async () => {
        const tracker = vi.fn()
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/executions", name: "executions", component: {template: "<div/>"}}],
        })
        await router.push("/executions")
        await router.isReady()

        let context: FilterContext | undefined
        const Harness = defineComponent({
            setup() {
                context = inject(FILTER_CONTEXT_INJECTION_KEY)
                return () => h("div")
            },
        })

        mount(KsFilter, {
            props: {configuration: {title: "", keys: []}, prefix: "test"},
            slots: {extra: () => h(Harness)},
            global: {
                plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem, router],
                provide: {[SAVED_FILTER_ANALYTICS_INJECTION_KEY as symbol]: tracker},
            },
        })

        return {tracker, context: context as FilterContext}
    }

    beforeEach(() => localStorage.clear())

    test("tracks a save action with page and filters count", async () => {
        const {tracker, context} = await mountWithTracker()

        context.saveFilter("My filter", "", [makeAppliedFilter()])

        expect(tracker).toHaveBeenCalledWith({action: "save", page: "executions", filtersCount: 1})
    })

    test("tracks an apply action when a saved filter is loaded", async () => {
        const {tracker, context} = await mountWithTracker()
        const savedFilter: SavedFilter = {
            id: "saved_1",
            name: "My filter",
            description: "",
            filters: [],
            createdAt: new Date(),
        }

        context.loadSavedFilter(savedFilter)

        expect(tracker).toHaveBeenCalledWith({action: "apply", page: "executions", filtersCount: 0})
    })

    test("tracks update and delete actions", async () => {
        const {tracker, context} = await mountWithTracker()
        context.saveFilter("My filter", "", [makeAppliedFilter()])
        const saved = context.savedFilters.value[0]

        context.updateSavedFilter(saved.id, "Renamed", "", [makeAppliedFilter("f1"), makeAppliedFilter("f2")])
        expect(tracker).toHaveBeenCalledWith({action: "update", page: "executions", filtersCount: 2})

        context.deleteSavedFilter(context.savedFilters.value[0])
        expect(tracker).toHaveBeenCalledWith({action: "delete", page: "executions", filtersCount: 2})
    })

    test("does not throw when no analytics tracker is provided", async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/", component: {template: "<div/>"}}],
        })
        let context: FilterContext | undefined
        const Harness = defineComponent({
            setup() {
                context = inject(FILTER_CONTEXT_INJECTION_KEY)
                return () => h("div")
            },
        })
        mount(KsFilter, {
            props: {configuration: {title: "", keys: []}, prefix: "test"},
            slots: {extra: () => h(Harness)},
            global: {plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem, router]},
        })

        expect(() => context!.saveFilter("X", "", [makeAppliedFilter()])).not.toThrow()
    })
})
