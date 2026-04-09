import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsLine from "../../../src/components/Charts/KsLine.vue"

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/charts", () => ({LineChart: {}}))

vi.mock("../../../src/components/Charts/KsEchart.vue", () => ({
    default: {
        name: "KsEchart",
        props: ["options", "loading", "tooltipType", "disableFeatures", "data", "renderer"],
        template: `<div
            class="ks-chart--line"
            :data-loading="String(loading)"
            :data-option="JSON.stringify(options)"
        />`,
    },
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getOption(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
    return JSON.parse(wrapper.find(".ks-chart--line").attributes("data-option") ?? "{}")
}

// ─── Container ────────────────────────────────────────────────────────────────

describe("KsLine — container", () => {
    test("applies ks-chart--line class", () => {
        const wrapper = mount(KsLine, {props: {loading: false}})
        expect(wrapper.find(".ks-chart--line").exists()).toBe(true)
    })
})

// ─── Loading ──────────────────────────────────────────────────────────────────

describe("KsLine — loading", () => {
    test("shows loading when data is null", () => {
        const wrapper = mount(KsLine, {props: {data: null}})
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("true")
    })

    test("shows loading when data is undefined (default)", () => {
        const wrapper = mount(KsLine)
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("true")
    })

    test("hides loading when data array is provided", () => {
        const wrapper = mount(KsLine, {props: {data: [{name: "A", data: [1, 2]}], loading: false}})
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("false")
    })

    test("loading prop true overrides data-based detection", () => {
        const wrapper = mount(KsLine, {props: {data: [{name: "A", data: [1]}], loading: true}})
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("true")
    })

    test("loading prop false overrides null data", () => {
        const wrapper = mount(KsLine, {props: {data: null, loading: false}})
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("false")
    })

    test("clears loading when data prop changes from null to array", async () => {
        const wrapper = mount(KsLine, {props: {data: null}})
        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("true")

        await wrapper.setProps({data: [{name: "A", data: [10, 20]}], loading: false})
        await nextTick()

        expect(wrapper.find(".ks-chart--line").attributes("data-loading")).toBe("false")
    })
})

// ─── Option ───────────────────────────────────────────────────────────────────

describe("KsLine — option", () => {
    test("series type is line", () => {
        const wrapper = mount(KsLine, {
            props: {data: [{name: "A", data: [1, 2]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].type).toBe("line")
    })

    test("xAxis boundaryGap is false", () => {
        const wrapper = mount(KsLine, {props: {loading: false}})
        const xAxis = getOption(wrapper).xAxis as Record<string, unknown>
        expect(xAxis.boundaryGap).toBe(false)
    })

    test("categories are passed to xAxis.data", () => {
        const wrapper = mount(KsLine, {
            props: {categories: ["Jan", "Feb"], loading: false},
        })
        const xAxis = getOption(wrapper).xAxis as Record<string, unknown>
        expect(xAxis.data).toEqual(["Jan", "Feb"])
    })
})

// ─── Options deep merge ────────────────────────────────────────────────────────

describe("KsLine — options deep merge", () => {
    test("user options override base option fields", () => {
        const wrapper = mount(KsLine, {
            props: {data: [], loading: false, options: {tooltip: {trigger: "item"}}},
        })
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.trigger).toBe("item")
    })
})
