import {describe, test, expect, vi, beforeAll} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsLine from "../../../src/components/Charts/KsLine.vue"

vi.mock("vue-echarts", () => ({
    default: {
        name: "VChart",
        props: ["option", "initOptions", "autoresize", "theme"],
        template: `<div class="v-chart-stub" :data-option="JSON.stringify(option)" />`,
    },
}))

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/renderers", () => ({CanvasRenderer: {}}))
vi.mock("echarts/charts", () => ({LineChart: {}}))
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

// ─── Mini mode ────────────────────────────────────────────────────────────────

describe("KsLine — mini container", () => {
    test("applies ks-mini-chart class when mini=true", () => {
        const wrapper = mount(KsLine, {props: {mini: true, loading: false}})
        expect(wrapper.find(".ks-mini-chart").exists()).toBe(true)
    })
})

describe("KsLine — mini loading", () => {
    test("shows loading when data is null", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: null}})
        expect(wrapper.find("[data-loading='true']").exists()).toBe(true)
    })

    test("hides loading when data is provided", () => {
        const wrapper = mount(KsLine, {
            props: {mini: true, data: [{name: "A", data: [1, 2, 3]}], loading: false},
        })
        expect(wrapper.find("[data-loading='false']").exists()).toBe(true)
    })

    test("clears loading when data prop changes", async () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: null}})
        await wrapper.setProps({data: [{name: "A", data: [1, 2]}], loading: false})
        await nextTick()
        expect(wrapper.find("[data-loading='false']").exists()).toBe(true)
    })
})

describe("KsLine — mini chrome stripped", () => {
    test("legend is hidden", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: [], loading: false}})
        const legend = getOption(wrapper).legend as Record<string, unknown>
        expect(legend.show).toBe(false)
    })

    test("xAxis is hidden", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: [], loading: false}})
        const xAxis = getOption(wrapper).xAxis as Record<string, unknown>
        expect(xAxis.show).toBe(false)
    })

    test("yAxis is hidden", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: [], loading: false}})
        const yAxis = getOption(wrapper).yAxis as Record<string, unknown>
        expect(yAxis.show).toBe(false)
    })

    test("tooltip is hidden", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: [], loading: false}})
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.show).toBe(false)
    })

    test("grid has minimal padding", () => {
        const wrapper = mount(KsLine, {props: {mini: true, data: [], loading: false}})
        const grid = getOption(wrapper).grid as Record<string, unknown>
        expect(grid.top).toBe(2)
        expect(grid.right).toBe(2)
        expect(grid.bottom).toBe(2)
        expect(grid.left).toBe(2)
    })
})

describe("KsLine — mini sparkline series", () => {
    test("series type is line", () => {
        const wrapper = mount(KsLine, {
            props: {mini: true, data: [{name: "A", data: [1, 2, 3]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].type).toBe("line")
    })

    test("symbol is none (no dots)", () => {
        const wrapper = mount(KsLine, {
            props: {mini: true, data: [{name: "A", data: [1, 2, 3]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].symbol).toBe("none")
    })

    test("areaStyle is injected with opacity", () => {
        const wrapper = mount(KsLine, {
            props: {mini: true, data: [{name: "A", data: [1, 2, 3]}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        const areaStyle = series[0].areaStyle as Record<string, unknown>
        expect(typeof areaStyle.opacity).toBe("number")
    })

    test("preserves existing areaStyle from data series", () => {
        const wrapper = mount(KsLine, {
            props: {
                mini: true,
                data: [{name: "A", data: [1, 2], areaStyle: {opacity: 0.5, color: "red"}}],
                loading: false,
            },
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        const areaStyle = series[0].areaStyle as Record<string, unknown>
        expect(areaStyle.opacity).toBe(0.5)
        expect(areaStyle.color).toBe("red")
    })

    test("symbol none applied to all series", () => {
        const wrapper = mount(KsLine, {
            props: {
                mini: true,
                data: [{name: "A", data: [1, 2]}, {name: "B", data: [3, 4]}],
                loading: false,
            },
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].symbol).toBe("none")
        expect(series[1].symbol).toBe("none")
    })
})

describe("KsLine — mini options override", () => {
    test("user options are merged over mini defaults", () => {
        const wrapper = mount(KsLine, {
            props: {mini: true, data: [], loading: false, options: {tooltip: {show: true}}},
        })
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.show).toBe(true)
    })
})
