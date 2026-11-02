import {describe, expect, it, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {computed, defineComponent} from "vue"

import {i18nMount} from "../../../../tests/unit/i18nMount"

const {route, state, generate} = await vi.hoisted(async () => {
    const {reactive} = await import("vue")
    return {
        route: reactive<{fullPath: string; params: Record<string, unknown>; query: Record<string, unknown>}>({fullPath: "/dashboards", params: {}, query: {}}),
        state: {
            data: undefined as {results?: Record<string, unknown>[]} | undefined,
            loading: false,
            percentageShown: false,
        },
        generate: vi.fn(),
    }
})

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({theme: "light"}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

vi.mock("../composables/useDashboards", async (importOriginal) => ({
    ...await importOriginal<typeof import("../composables/useDashboards")>(),
    useChartGenerator: () => ({
        percentageShown: computed(() => state.percentageShown),
        data: computed(() => state.data),
        loading: computed(() => state.loading),
        generate,
    }),
}))

import KPI from "./KPI.vue"
import type {Chart} from "../composables/useDashboards"

const KsSkeletonStub = defineComponent({name: "KsSkeleton", template: "<div class=\"ks-skeleton-stub\" />"})

const KsProgressStub = defineComponent({
    name: "KsProgress",
    props: {percentage: {type: Number, default: undefined}},
    template: "<span :data-percentage=\"percentage\" />",
})

const mountKpi = (chartOptions: Chart["chartOptions"] = {}) =>
    i18nMount(KPI, {
        props: {
            dashboardId: "d1",
            chart: {id: "executions", type: "io.kestra.plugin.core.dashboard.chart.KPI", chartOptions},
        },
        global: {stubs: {KsSkeleton: KsSkeletonStub, KsProgress: KsProgressStub}},
    })

const mountWithData = (value: unknown, percentage = false, chartOptions: Chart["chartOptions"] = {}) => {
    state.data = {results: [{value}]}
    state.percentageShown = percentage
    return mountKpi(chartOptions)
}

describe("dashboard KPI section", () => {
    beforeEach(() => {
        generate.mockReset()
        state.data = undefined
        state.loading = false
        state.percentageShown = false
    })

    afterEach(() => {
        route.fullPath = "/dashboards"
    })

    it("renders a skeleton while loading with no data yet", () => {
        state.loading = true
        const wrapper = mountKpi()

        expect(wrapper.find(".ks-skeleton-stub").exists()).toBe(true)
        expect(wrapper.find("section.kpi").exists()).toBe(false)
    })

    it("renders the title and the value once the data arrives", () => {
        const wrapper = mountWithData(42, false, {displayName: "Executions count"})

        expect(wrapper.find("span.title").text()).toBe("Executions count")
        expect(wrapper.find("p.value").text()).toBe("42")
        expect(wrapper.find("span.percent").exists()).toBe(false)
    })

    it("shows the percent sign only when the chart is a percentage", () => {
        state.data = {results: [{value: 42}]}
        expect(mountKpi().find("span.percent").exists()).toBe(false)

        state.percentageShown = true
        expect(mountKpi().find("span.percent").exists()).toBe(true)
    })

    it("renders the progress bar only when a percentage and above zero", () => {
        const wrapper = mountWithData(50, true)
        const bar = wrapper.find("span.progress")
        expect(bar.exists()).toBe(true)
        expect(bar.attributes("data-percentage")).toBe("50")
        expect(mountWithData(50, false).find("span.progress").exists()).toBe(false)
        expect(mountWithData(0, true).find("span.progress").exists()).toBe(false)
    })

    it("clamps a value above 100 to 100", () => {
        const wrapper = mountWithData(150, true)

        expect(wrapper.find("span.progress").attributes("data-percentage")).toBe("100")
    })

    it("hides the bar for a non-finite value rather than rendering a broken one", () => {
        expect(mountWithData(Infinity, true).find("span.progress").exists()).toBe(false)
    })

    it("triggers a regenerate through the exposed refresh()", async () => {
        const wrapper = mountWithData(42)
        expect(generate).not.toHaveBeenCalled()

        await (wrapper.vm as unknown as {refresh: () => Promise<unknown>}).refresh()

        expect(generate).toHaveBeenCalledTimes(1)
    })

    it("triggers a regenerate when the route changes", async () => {
        mountWithData(42)
        expect(generate).not.toHaveBeenCalled()

        route.fullPath = "/dashboards?filters[state][IN]=SUCCESS"
        await flushPromises()

        expect(generate).toHaveBeenCalledTimes(1)
    })

    it("does not regenerate when the route object is replaced without a URL change", async () => {
        mountWithData(42)

        route.params = {}
        route.query = {}
        await flushPromises()

        expect(generate).not.toHaveBeenCalled()
    })
})
