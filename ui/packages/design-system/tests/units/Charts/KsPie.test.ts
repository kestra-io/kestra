import {describe, test, expect, vi, beforeAll} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick, ref} from "vue"
import KsPie from "../../../src/components/Charts/KsPie.vue"

// jsdom has no layout — force non-zero dimensions so KsEchart's canRender
// latch flips and <VChart> mounts.
vi.mock("@vueuse/core", () => ({
    useElementSize: () => ({width: ref(800), height: ref(400)}),
}))

vi.mock("vue-echarts", () => ({
    default: {
        name: "VChart",
        props: ["option", "initOptions", "autoresize", "theme"],
        template: "<div class=\"v-chart-stub\" :data-option=\"JSON.stringify(option)\" />",
    },
}))

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/renderers", () => ({CanvasRenderer: {}, SVGRenderer: {}}))
vi.mock("echarts/charts", () => ({PieChart: {}}))
vi.mock("echarts/components", () => ({
    GridComponent: {},
    TooltipComponent: {},
    LegendComponent: {},
    DataZoomComponent: {},
    GraphicComponent: {},
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
        template: "<div class=\"ks-tooltip-stub\"><slot /></div>",
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

describe("KsPie — container", () => {
    test("applies ks-chart--pie class", () => {
        const wrapper = mount(KsPie, {props: {loading: false}})
        expect(wrapper.find(".ks-chart--pie").exists()).toBe(true)
    })
})

// ─── Loading ──────────────────────────────────────────────────────────────────

describe("KsPie — loading", () => {
    test("shows loading when data is null", () => {
        const wrapper = mount(KsPie, {props: {data: null}})
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("true")
    })

    test("shows loading when data is undefined (default)", () => {
        const wrapper = mount(KsPie)
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("true")
    })

    test("hides loading when data array is provided", () => {
        const wrapper = mount(KsPie, {props: {data: [{name: "A", value: 10}], loading: false}})
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("false")
    })

    test("loading prop true overrides data-based detection", () => {
        const wrapper = mount(KsPie, {props: {data: [{name: "A", value: 10}], loading: true}})
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("true")
    })

    test("loading prop false overrides null data", () => {
        const wrapper = mount(KsPie, {props: {data: null, loading: false}})
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("false")
    })

    test("clears loading when data prop changes from null to array", async () => {
        const wrapper = mount(KsPie, {props: {data: null}})
        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("true")

        await wrapper.setProps({data: [{name: "A", value: 10}], loading: false})
        await nextTick()

        expect(wrapper.find(".ks-chart--pie").attributes("data-loading")).toBe("false")
    })
})

// ─── Option ───────────────────────────────────────────────────────────────────

describe("KsPie — option", () => {
    test("series type is pie", () => {
        const wrapper = mount(KsPie, {
            props: {data: [{name: "A", value: 10}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].type).toBe("pie")
    })

    test("data items are mapped to series[0].data", () => {
        const data = [{name: "A", value: 10}, {name: "B", value: 20}]
        const wrapper = mount(KsPie, {props: {data, loading: false}})
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(series[0].data).toEqual(data)
    })

    test("donut=true sets array radius", () => {
        const wrapper = mount(KsPie, {
            props: {donut: true, data: [{name: "A", value: 1}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(Array.isArray(series[0].radius)).toBe(true)
    })

    test("donut=false sets string radius", () => {
        const wrapper = mount(KsPie, {
            props: {donut: false, data: [{name: "A", value: 1}], loading: false},
        })
        const series = getOption(wrapper).series as Record<string, unknown>[]
        expect(typeof series[0].radius).toBe("string")
    })
})

// ─── Options deep merge ────────────────────────────────────────────────────────

describe("KsPie — options deep merge", () => {
    test("user options override base option fields", () => {
        const wrapper = mount(KsPie, {
            props: {data: [], loading: false, options: {tooltip: {trigger: "axis"}}},
        })
        const tooltip = getOption(wrapper).tooltip as Record<string, unknown>
        expect(tooltip.trigger).toBe("axis")
    })
})
