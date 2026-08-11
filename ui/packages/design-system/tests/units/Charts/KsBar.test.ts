import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsBar from "../../../src/components/Charts/KsBar.vue"

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/charts", () => ({BarChart: {}}))

vi.mock("../../../src/components/Charts/KsEchart.vue", () => ({
    default: {
        name: "KsEchart",
        props: ["options", "loading", "tooltipType", "disableFeatures", "data", "renderer"],
        template: `<div
            class="ks-chart--bar"
            :data-loading="String(loading)"
            :data-option="JSON.stringify(options)"
        />`,
    },
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getOption(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
    return JSON.parse(wrapper.find(".ks-chart--bar").attributes("data-option") ?? "{}")
}

// ─── Container ────────────────────────────────────────────────────────────────

describe("KsBar — container", () => {
    test("applies ks-chart--bar class", () => {
        const wrapper = mount(KsBar, {props: {loading: false}})
        expect(wrapper.find(".ks-chart--bar").exists()).toBe(true)
    })
})

// ─── Loading ──────────────────────────────────────────────────────────────────

describe("KsBar — loading", () => {
    test("shows loading when data is null", () => {
        const wrapper = mount(KsBar, {props: {data: null}})
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("true")
    })

    test("shows loading when data is undefined (default)", () => {
        const wrapper = mount(KsBar)
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("true")
    })

    test("hides loading when data array is provided", () => {
        const wrapper = mount(KsBar, {props: {data: [{name: "A", data: [1, 2]}], loading: false}})
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("false")
    })

    test("loading prop true overrides data-based detection", () => {
        const wrapper = mount(KsBar, {props: {data: [{name: "A", data: [1]}], loading: true}})
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("true")
    })

    test("loading prop false overrides null data", () => {
        const wrapper = mount(KsBar, {props: {data: null, loading: false}})
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("false")
    })

    test("clears loading when data prop changes from null to array", async () => {
        const wrapper = mount(KsBar, {props: {data: null}})
        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("true")

        await wrapper.setProps({data: [{name: "A", data: [10, 20]}], loading: false})
        await nextTick()

        expect(wrapper.find(".ks-chart--bar").attributes("data-loading")).toBe("false")
    })
})

// ─── Option ───────────────────────────────────────────────────────────────────

describe("KsBar — option", () => {
    test("series type is bar", () => {
        const wrapper = mount(KsBar, {
            props: {data: [{name: "A", data: [1, 2]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].type).toBe("bar")
    })

    test("categories are passed to xAxis.data", () => {
        const wrapper = mount(KsBar, {
            props: {categories: ["Jan", "Feb"], loading: false},
        })
        const xAxis = getOption(wrapper).xAxis as Record<string, unknown>
        expect(xAxis.data).toEqual(["Jan", "Feb"])
    })

    test("stack prop adds stack:'total' to all series", () => {
        const wrapper = mount(KsBar, {
            props: {
                stack: true,
                data: [{name: "A", data: [1, 2]}, {name: "B", data: [3, 4]}],
                loading: false,
            },
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].stack).toBe("total")
        expect(series[1].stack).toBe("total")
    })

    test("stack=false omits stack property", () => {
        const wrapper = mount(KsBar, {
            props: {stack: false, data: [{name: "A", data: [1]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].stack).toBeUndefined()
    })
})

// ─── Options deep merge ────────────────────────────────────────────────────────

describe("KsBar — options deep merge", () => {
    test("user options override base option fields", () => {
        const wrapper = mount(KsBar, {
            props: {data: [], loading: false, options: {tooltip: {trigger: "item"}}},
        })
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.trigger).toBe("item")
    })
})
