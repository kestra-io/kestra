import {beforeEach, describe, expect, it, vi} from "vitest"
import {computed, nextTick, reactive, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {KsProgress, KsSkeleton} from "@kestra-io/design-system"
import type {Chart, ChartResults} from "../types"

const generatorState = {
    percentageShown: ref(false),
    data: ref<ChartResults | null>(null),
    loading: ref(false),
}

const mockGenerate = vi.fn().mockResolvedValue(undefined)

const routeState = reactive({
    params: {
        filters: "" as string | undefined,
    },
})

vi.mock("vue-router", () => ({
    useRoute: () => routeState,
}))

vi.mock("../../../composables/useTheme", () => ({
    useTheme: () => computed(() => "light" as const),
}))

vi.mock("../composables/useDashboards", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../composables/useDashboards")>()
    return {
        ...actual,
        useChartGenerator: () => ({
            percentageShown: generatorState.percentageShown,
            data: generatorState.data,
            loading: generatorState.loading,
            generate: mockGenerate,
        }),
    }
})

import KPI from "./KPI.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const defaultChart: Chart = {
    id: "kpi-chart",
    type: "io.kestra.plugin.core.dashboard.chart.KPI",
    chartOptions: {
        displayName: "Total Executions",
    },
}

function mountKPI(chart = defaultChart) {
    return i18nMount(KPI, {
        props: {
            chart,
        },
        global: {
            plugins: [createPinia()],
            stubs: {
                KsSkeleton: {
                    name: "KsSkeleton",
                    template: "<div class=\"ks-skeleton-stub\" />",
                },
                KsProgress: {
                    name: "KsProgress",
                    props: ["percentage", "color", "strokeWidth", "radius", "showText"],
                    template: "<div class=\"ks-progress-stub\" :data-percentage=\"percentage\" />",
                },
                KsNoData: {
                    name: "KsNoData",
                    template: "<div class=\"ks-no-data-stub\" />",
                },
            },
        },
    })
}

describe("KPI.vue", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        generatorState.percentageShown.value = false
        generatorState.data.value = null
        generatorState.loading.value = false
        routeState.params.filters = ""
    })

    it("renders a skeleton while loading with no data yet", () => {
        generatorState.loading.value = true
        generatorState.data.value = null

        const wrapper = mountKPI()
        expect(wrapper.findComponent(KsSkeleton).exists()).toBe(true)
        expect(wrapper.find(".kpi").exists()).toBe(false)
    })

    it("renders the value and title once data arrives", () => {
        generatorState.loading.value = false
        generatorState.data.value = {
            results: [{value: "1234"}],
        }

        const wrapper = mountKPI()
        expect(wrapper.findComponent(KsSkeleton).exists()).toBe(false)
        expect(wrapper.find(".title").text()).toBe("Total Executions")
        expect(wrapper.find(".value").text()).toContain("1234")
    })

    it("shows the percent sign only when the chart is a percentage", () => {
        generatorState.data.value = {
            results: [{value: "85"}],
        }

        // percentageShown is false
        generatorState.percentageShown.value = false
        let wrapper = mountKPI()
        expect(wrapper.find(".percent").exists()).toBe(false)

        // percentageShown is true
        generatorState.percentageShown.value = true
        wrapper = mountKPI()
        expect(wrapper.find(".percent").exists()).toBe(true)
        expect(wrapper.find(".percent").text()).toBe("%")
    })

    it("renders the progress bar only when a percentage and above zero", () => {
        // Not a percentage
        generatorState.percentageShown.value = false
        generatorState.data.value = {results: [{value: "50"}]}
        let wrapper = mountKPI()
        expect(wrapper.findComponent(KsProgress).exists()).toBe(false)

        // Percentage but value is 0
        generatorState.percentageShown.value = true
        generatorState.data.value = {results: [{value: "0"}]}
        wrapper = mountKPI()
        expect(wrapper.findComponent(KsProgress).exists()).toBe(false)

        // Percentage and above zero
        generatorState.data.value = {results: [{value: "75"}]}
        wrapper = mountKPI()
        expect(wrapper.findComponent(KsProgress).exists()).toBe(true)
        expect(wrapper.findComponent(KsProgress).props("percentage")).toBe(75)
    })

    it("clamps a value above 100 to 100 and a negative one to 0", () => {
        generatorState.percentageShown.value = true

        // Value above 100
        generatorState.data.value = {results: [{value: "150"}]}
        let wrapper = mountKPI()
        expect(wrapper.findComponent(KsProgress).exists()).toBe(true)
        expect(wrapper.findComponent(KsProgress).props("percentage")).toBe(100)

        // Negative value
        generatorState.data.value = {results: [{value: "-10"}]}
        wrapper = mountKPI()
        // progressValue is 0, so progress bar is not displayed
        expect(wrapper.findComponent(KsProgress).exists()).toBe(false)
    })

    it("yields 0 rather than NaN for a non-numeric value", () => {
        generatorState.percentageShown.value = true
        generatorState.data.value = {results: [{value: "not-a-number"}]}

        const wrapper = mountKPI()
        // progressValue evaluates to 0, progress bar is hidden
        expect(wrapper.findComponent(KsProgress).exists()).toBe(false)
    })

    it("triggers regenerate when exposed refresh is called", () => {
        const wrapper = mountKPI()
        const vm = wrapper.vm as unknown as {refresh: () => void}

        expect(mockGenerate).not.toHaveBeenCalled()
        vm.refresh()
        expect(mockGenerate).toHaveBeenCalledTimes(1)
    })

    it("triggers a refresh when route filters change", async () => {
        mountKPI()
        mockGenerate.mockClear()

        routeState.params.filters = "namespace:company.team"
        await nextTick()
        await flushPromises()

        expect(mockGenerate).toHaveBeenCalledTimes(1)
    })
})
