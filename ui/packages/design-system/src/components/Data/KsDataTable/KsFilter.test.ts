import {describe, test, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, h, inject} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"
import KsFilter from "./KsFilter.vue"
import FilterEditPopper from "./filter/layout/FilterEditPopper.vue"
import FilterFooter from "./filter/layout/FilterFooter.vue"
import {FILTER_CONTEXT_INJECTION_KEY} from "./filter/utils/filterInjectionKeys"
import {SAVED_FILTER_ANALYTICS_INJECTION_KEY} from "./filter/utils/filterAnalytics"
import {Comparators, type FilterKeyConfig} from "./filter/utils/filterTypes"
import type {AppliedFilter, FilterContext, SavedFilter} from "../../../index"
import {i18nMount} from "../../../../tests/units/i18nMount"

const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/", component: {template: "<div/>"}}],
})

const globalConfig = {
    plugins: [router],
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
        const wrapper = i18nMount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter").exists()).toBe(true)
    })

    test("renders filter section with top div", () => {
        const wrapper = i18nMount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.find(".filter .top").exists()).toBe(true)
    })

    test("emits filter event when appliedFilters change", async () => {
        const wrapper = i18nMount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
            },
            global: globalConfig,
        })
        expect(wrapper.emitted()).toBeTruthy()
    })

    test("does not render filter options when showOptions is false", () => {
        const wrapper = i18nMount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
                tableOptions: {},
            },
            global: globalConfig,
        })
        // FilterOptions is hidden by default (showOptions starts false)
        expect(wrapper.find(".expand-panel").exists()).toBe(false)
    })

    test("returnsTheRefreshCallbackPromiseAndInvokesItOnce", async () => {
        let context: FilterContext | undefined
        const Harness = defineComponent({
            setup() {
                context = inject(FILTER_CONTEXT_INJECTION_KEY)
                return () => h("div")
            },
        })
        const refreshPromise = Promise.resolve("refreshed")
        const callback = vi.fn(() => refreshPromise)
        const wrapper = i18nMount(KsFilter, {
            props: {
                configuration: {title: "", keys: []},
                tableOptions: {refresh: {shown: true, callback}},
            },
            slots: {extra: () => h(Harness)},
            global: globalConfig,
        })

        const result = context!.refreshData()

        expect(result).toBe(refreshPromise)
        expect(callback).toHaveBeenCalledTimes(1)
        expect(wrapper.emitted("refresh")).toHaveLength(1)
        await result
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
        const testRouter = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/executions", name: "executions", component: {template: "<div/>"}}],
        })
        await testRouter.push("/executions")
        await testRouter.isReady()

        let context: FilterContext | undefined
        const Harness = defineComponent({
            setup() {
                context = inject(FILTER_CONTEXT_INJECTION_KEY)
                return () => h("div")
            },
        })

        i18nMount(KsFilter, {
            props: {configuration: {title: "", keys: []}, prefix: "test"},
            slots: {extra: () => h(Harness)},
            global: {
                plugins: [testRouter],
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
        const testRouter = createRouter({
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
        i18nMount(KsFilter, {
            props: {configuration: {title: "", keys: []}, prefix: "test"},
            slots: {extra: () => h(Harness)},
            global: {plugins: [testRouter]},
        })

        expect(() => context!.saveFilter("X", "", [makeAppliedFilter()])).not.toThrow()
    })
})

describe("KsFilter reset to default", () => {
    const timeRangeKey: FilterKeyConfig = {
        key: "timeRange",
        label: "Interval",
        valueType: "time-range",
        comparators: [Comparators.EQUALS],
        valueProvider: async () => [
            {label: "Last 24 hours", value: "PT24H"},
            {label: "Last 7 days", value: "P7D"},
            {label: "Last 30 days", value: "PT720H"},
        ],
    }

    const stateKey: FilterKeyConfig = {
        key: "state",
        label: "State",
        valueType: "multi-select",
        visibleByDefault: true,
        comparators: [Comparators.IN],
    }

    const sevenDays: AppliedFilter = {
        id: "tr1",
        key: "timeRange",
        keyLabel: "Started",
        comparator: Comparators.EQUALS,
        comparatorLabel: "Equals",
        value: "P7D",
        valueLabel: "Last 7 days",
    }

    const mountPopperInFilter = async (props: {defaultDuration?: string} = {}) => {
        const testRouter = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/", component: {template: "<div/>"}}],
        })
        await testRouter.push("/")
        await testRouter.isReady()

        const wrapper = i18nMount(KsFilter, {
            props: {configuration: {title: "", keys: [stateKey, timeRangeKey]}, prefix: "test", ...props},
            slots: {extra: () => h(FilterEditPopper, {filter: sevenDays, filterKey: timeRangeKey})},
            global: {plugins: [testRouter]},
        })
        await flushPromises()
        return wrapper
    }

    const lastAppliedValue = (wrapper: Awaited<ReturnType<typeof mountPopperInFilter>>) => {
        const popper = wrapper.findComponent(FilterEditPopper)
        const updates = popper.emitted("update") as Array<[AppliedFilter]> | undefined
        return updates?.at(-1)?.[0].value
    }

    beforeEach(() => localStorage.clear())

    test("restores the page default interval when the interval key declares no defaultValue", async () => {
        const wrapper = await mountPopperInFilter()

        wrapper.findComponent(FilterFooter).vm.$emit("reset")
        await flushPromises()

        expect(lastAppliedValue(wrapper)).toBe("PT24H")
    })

    test("restores the default duration the page was given", async () => {
        const wrapper = await mountPopperInFilter({defaultDuration: "P30D"})

        wrapper.findComponent(FilterFooter).vm.$emit("reset")
        await flushPromises()

        expect(lastAppliedValue(wrapper)).toBe("PT720H")
    })
})
