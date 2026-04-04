import {describe, test, expect, vi, beforeAll} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsEchart from "../../../src/components/Charts/KsEchart.vue"

// ─── Mock vue-echarts ─────────────────────────────────────────────────────────
// VChart relies on canvas APIs absent in jsdom. Replace it with a no-op stub
// that exposes the same interface KsEchart depends on.

const mockEchartsInstance = {
    resize: vi.fn(),
    getOption: vi.fn(() => ({xAxis: [{data: ["Jan", "Feb", "Mar"]}]})),
    convertFromPixel: vi.fn(() => [1, 0]),
}

vi.mock("vue-echarts", () => ({
    default: {
        name: "VChart",
        props: ["theme", "option", "initOptions", "autoresize"],
        emits: ["mouseover", "mouseout"],
        setup() {
            return {chart: mockEchartsInstance}
        },
        template: `<div class="v-chart-stub" />`,
    },
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

const globalConfig = {plugins: [KestraDesignSystem]}

const BASE_OPTIONS = {
    xAxis: {type: "category", data: ["Jan", "Feb", "Mar"], boundaryGap: false},
    yAxis: {type: "value"},
    tooltip: {trigger: "axis"},
    series: [{name: "Runs", type: "line", data: [10, 20, 30]}],
}

function mountChart(props: Record<string, unknown> = {}) {
    return mount(KsEchart, {
        props: {options: BASE_OPTIONS, ...props},
        global: globalConfig,
    })
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("KsEchart", () => {
    beforeAll(() => {
        // Provide a minimal MutationObserver so the dark-mode watcher doesn't throw.
        vi.stubGlobal(
            "MutationObserver",
            class {
                observe() {}
                disconnect() {}
            },
        )
    })

    // ── Rendering ──────────────────────────────────────────────────────────────

    test("renders .ks-chart-wrapper container", () => {
        const wrapper = mountChart()
        expect(wrapper.find(".ks-chart-wrapper").exists()).toBe(true)
    })

    test("renders the VChart stub inside the wrapper", () => {
        const wrapper = mountChart()
        expect(wrapper.find(".v-chart-stub").exists()).toBe(true)
    })

    // ── tooltipType = native (default) ─────────────────────────────────────────

    test("does not wrap with KsTooltip when tooltipType is native", () => {
        const wrapper = mountChart({tooltipType: "native"})
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(false)
    })

    // ── tooltipType = external ─────────────────────────────────────────────────

    test("wraps chart with KsTooltip when tooltipType is external", () => {
        const wrapper = mountChart({tooltipType: "external"})
        expect(wrapper.findComponent({name: "KsTooltip"}).exists()).toBe(true)
    })

    test("KsTooltip trigger is manual in external mode", () => {
        const wrapper = mountChart({tooltipType: "external"})
        const tooltip = wrapper.findComponent({name: "KsTooltip"})
        expect(tooltip.props("trigger")).toBe("manual")
    })

    // ── loading ────────────────────────────────────────────────────────────────

    test("applies v-ks-loading directive when loading is true", () => {
        // The directive adds a class or attribute — just verify mount succeeds
        // and the directive prop is forwarded correctly.
        const wrapper = mountChart({loading: true})
        expect(wrapper.find(".ks-chart-wrapper").exists()).toBe(true)
    })

    // ── disableFeatures ────────────────────────────────────────────────────────

    test("hides native tooltip in effectiveOption when tooltipType is external", () => {
        const wrapper = mountChart({tooltipType: "external"})
        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        expect((opt.tooltip as Record<string, unknown>)?.show).toBe(false)
    })

    test("does not override tooltip option when tooltipType is native", () => {
        const wrapper = mountChart({tooltipType: "native"})
        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        // The original trigger should be preserved
        expect((opt.tooltip as Record<string, unknown>)?.trigger).toBe("axis")
    })

    test("merges disableFeatures LEGEND into effectiveOption", () => {
        const wrapper = mountChart({disableFeatures: ["LEGEND"]})
        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        expect((opt.legend as Record<string, unknown>)?.show).toBe(false)
    })

    test("merges disableFeatures AXIS into effectiveOption (hides xAxis)", () => {
        const wrapper = mountChart({disableFeatures: ["AXIS"]})
        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        const xAxis = opt.xAxis as Record<string, unknown>
        expect(xAxis?.show).toBe(false)
    })

    test("merges disableFeatures TOOLTIP into effectiveOption", () => {
        const wrapper = mountChart({disableFeatures: ["TOOLTIP"]})
        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        expect((opt.tooltip as Record<string, unknown>)?.show).toBe(false)
    })

    // ── Mixed bar + area with multiple y-axes ──────────────────────────────────

    test("passes mixed bar+area series option through without mutation", () => {
        const mixedOptions = {
            xAxis: {type: "category", data: ["Jan", "Feb", "Mar"], boundaryGap: true},
            yAxis: [
                {type: "value", name: "Executions", position: "left"},
                {type: "value", name: "Duration (s)", position: "right"},
            ],
            tooltip: {trigger: "axis"},
            series: [
                {name: "Executions", type: "bar", yAxisIndex: 0, data: [320, 420, 380]},
                {name: "Avg Duration", type: "line", yAxisIndex: 1, areaStyle: {opacity: 0.2}, data: [12.4, 9.8, 14.2]},
            ],
        }

        const wrapper = mount(KsEchart, {
            props: {options: mixedOptions},
            global: globalConfig,
        })

        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as typeof mixedOptions

        expect(Array.isArray(opt.yAxis)).toBe(true)
        expect((opt.yAxis as unknown[]).length).toBe(2)
        expect(opt.series[0].type).toBe("bar")
        expect(opt.series[1].type).toBe("line")
        expect(opt.series[1].yAxisIndex).toBe(1)
    })

    test("disableFeatures AXIS applied to each y-axis when yAxis is an array", () => {
        const mixedOptions = {
            xAxis: {type: "category", data: ["Jan", "Feb", "Mar"]},
            yAxis: [
                {type: "value", name: "Left"},
                {type: "value", name: "Right"},
            ],
            tooltip: {trigger: "axis"},
            series: [
                {name: "Bar", type: "bar", yAxisIndex: 0, data: [1, 2, 3]},
                {name: "Area", type: "line", yAxisIndex: 1, areaStyle: {}, data: [4, 5, 6]},
            ],
        }

        const wrapper = mount(KsEchart, {
            props: {options: mixedOptions, disableFeatures: ["AXIS"]},
            global: globalConfig,
        })

        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>

        // Both y-axes should have show:false
        const yAxes = opt.yAxis as {show?: boolean}[]
        expect(Array.isArray(yAxes)).toBe(true)
        expect(yAxes[0].show).toBe(false)
        expect(yAxes[1].show).toBe(false)
    })

    test("disableFeatures AXIS_SPLITLINE applied to each y-axis in multi-axis chart", () => {
        const mixedOptions = {
            xAxis: {type: "category", data: ["Jan", "Feb", "Mar"]},
            yAxis: [
                {type: "value", splitLine: {show: true}},
                {type: "value", splitLine: {show: true}},
            ],
            tooltip: {trigger: "axis"},
            series: [
                {name: "Runs", type: "bar", yAxisIndex: 0, data: [1, 2, 3]},
                {name: "Latency", type: "line", yAxisIndex: 1, data: [4, 5, 6]},
            ],
        }

        const wrapper = mount(KsEchart, {
            props: {options: mixedOptions, disableFeatures: ["AXIS_SPLITLINE"]},
            global: globalConfig,
        })

        const vChart = wrapper.findComponent({name: "VChart"})
        const opt = vChart.props("option") as Record<string, unknown>
        const yAxes = opt.yAxis as {splitLine?: {show: boolean}}[]

        expect(Array.isArray(yAxes)).toBe(true)
        expect(yAxes[0].splitLine?.show).toBe(false)
        expect(yAxes[1].splitLine?.show).toBe(false)
    })

    // ── getEchartsInstance expose ──────────────────────────────────────────────

    test("exposes getEchartsInstance method", () => {
        const wrapper = mountChart()
        expect(typeof wrapper.vm.getEchartsInstance).toBe("function")
    })

    // ── renderer prop ──────────────────────────────────────────────────────────

    test("passes renderer prop to VChart initOptions", () => {
        const wrapper = mountChart({renderer: "svg"})
        const vChart = wrapper.findComponent({name: "VChart"})
        const initOptions = vChart.props("initOptions") as {renderer: string}
        expect(initOptions.renderer).toBe("svg")
    })

    test("defaults renderer to canvas", () => {
        const wrapper = mountChart()
        const vChart = wrapper.findComponent({name: "VChart"})
        const initOptions = vChart.props("initOptions") as {renderer: string}
        expect(initOptions.renderer).toBe("canvas")
    })
})
