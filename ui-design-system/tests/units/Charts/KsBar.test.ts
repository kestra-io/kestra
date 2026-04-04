import {describe, test, expect, vi, beforeAll} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsBar from "../../../src/components/Charts/KsBar.vue"

vi.mock("vue-echarts", () => ({
    default: {
        name: "VChart",
        props: ["option", "initOptions", "autoresize", "theme"],
        template: `<div class="v-chart-stub" :data-option="JSON.stringify(option)" />`,
    },
}))

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/renderers", () => ({CanvasRenderer: {}}))
vi.mock("echarts/charts", () => ({BarChart: {}}))
vi.mock("echarts/components", () => ({
    GridComponent: {},
    TooltipComponent: {},
    LegendComponent: {},
    DataZoomComponent: {},
}))

vi.mock("../../../src/components/Feedback/KsLoading", () => ({
    vKsLoading: {
        mounted(el: HTMLElement, binding: {value: boolean}) {
            el.setAttribute("data-loading", String(binding.value))
        },
        updated(el: HTMLElement, binding: {value: boolean}) {
            el.setAttribute("data-loading", String(binding.value))
        },
    },
}))

vi.mock("../../../src/components/Feedback/KsTooltip.vue", () => ({
    default: {
        name: "KsTooltip",
        props: ["trigger", "visible", "content", "rawContent", "placement"],
        template: `<div class="ks-tooltip-stub"><slot /></div>`,
    },
}))

beforeAll(() => {
    vi.spyOn(window, "getComputedStyle").mockReturnValue({
        getPropertyValue: () => "",
    } as unknown as CSSStyleDeclaration)
})

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getOption(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
    return JSON.parse(wrapper.find(".v-chart-stub").attributes("data-option") ?? "{}")
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

// ─── Mini mode ────────────────────────────────────────────────────────────────

describe("KsBar — mini container", () => {
    test("applies ks-mini-chart class when mini=true", () => {
        const wrapper = mount(KsBar, {props: {mini: true, loading: false}})
        expect(wrapper.find(".ks-mini-chart").exists()).toBe(true)
    })
})

describe("KsBar — mini chrome stripped", () => {
    test("legend is hidden", () => {
        const wrapper = mount(KsBar, {props: {mini: true, data: [], loading: false}})
        const legend = getOption(wrapper).legend as Record<string, unknown>
        expect(legend.show).toBe(false)
    })

    test("xAxis is hidden", () => {
        const wrapper = mount(KsBar, {props: {mini: true, data: [], loading: false}})
        const xAxis = getOption(wrapper).xAxis as Record<string, unknown>
        expect(xAxis.show).toBe(false)
    })

    test("yAxis is hidden", () => {
        const wrapper = mount(KsBar, {props: {mini: true, data: [], loading: false}})
        const yAxis = getOption(wrapper).yAxis as Record<string, unknown>
        expect(yAxis.show).toBe(false)
    })

    test("tooltip is hidden", () => {
        const wrapper = mount(KsBar, {props: {mini: true, data: [], loading: false}})
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.show).toBe(false)
    })

    test("grid has minimal padding", () => {
        const wrapper = mount(KsBar, {props: {mini: true, data: [], loading: false}})
        const grid = getOption(wrapper).grid as Record<string, unknown>
        expect(grid.top).toBe(2)
        expect(grid.right).toBe(2)
        expect(grid.bottom).toBe(2)
        expect(grid.left).toBe(2)
    })
})

describe("KsBar — mini bar series", () => {
    test("series type is bar", () => {
        const wrapper = mount(KsBar, {
            props: {mini: true, data: [{name: "A", data: [1, 2, 3]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].type).toBe("bar")
    })

    test("bar data is not modified (no symbol injection)", () => {
        const wrapper = mount(KsBar, {
            props: {mini: true, data: [{name: "A", data: [10, 20]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].symbol).toBeUndefined()
    })

    test("stack prop stacks bar series in mini mode", () => {
        const wrapper = mount(KsBar, {
            props: {
                mini: true,
                stack: true,
                data: [{name: "A", data: [1, 2]}, {name: "B", data: [3, 4]}],
                loading: false,
            },
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].stack).toBe("total")
        expect(series[1].stack).toBe("total")
    })
})

describe("KsBar — mini options override", () => {
    test("user options are merged over mini defaults", () => {
        const wrapper = mount(KsBar, {
            props: {mini: true, data: [], loading: false, options: {tooltip: {show: true}}},
        })
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.show).toBe(true)
    })
})
