import {describe, test, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsGraph from "../../../src/components/Charts/KsGraph.vue"

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/charts", () => ({GraphChart: {}}))

// ─── Static mock (getEchartsInstance returns null) ─────────────────────────────
// Used for option-building tests that don't exercise event handling.
vi.mock("../../../src/components/Charts/KsEchart.vue", () => ({
    default: {
        name: "KsEchart",
        props: ["options", "loading", "tooltipType", "disableFeatures", "data", "renderer"],
        template: `<div
            class="ks-chart--graph"
            :data-loading="String(loading)"
            :data-option="JSON.stringify(options)"
        />`,
        methods: {
            getEchartsInstance() {
                return null
            },
        },
    },
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getOption(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
    return JSON.parse(wrapper.find(".ks-chart--graph").attributes("data-option") ?? "{}")
}

function getSeries(wrapper: ReturnType<typeof mount>): Record<string, unknown> {
    const series = getOption(wrapper).series as Record<string, unknown>[]
    return series[0]
}

// ─── Container ────────────────────────────────────────────────────────────────

describe("KsGraph — container", () => {
    test("applies ks-chart--graph class", () => {
        const wrapper = mount(KsGraph, {props: {loading: false}})
        expect(wrapper.find(".ks-chart--graph").exists()).toBe(true)
    })
})

// ─── Loading ──────────────────────────────────────────────────────────────────

describe("KsGraph — loading", () => {
    test("shows loading when nodes is null", () => {
        const wrapper = mount(KsGraph, {props: {nodes: null}})
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("true")
    })

    test("shows loading when nodes is undefined (default)", () => {
        const wrapper = mount(KsGraph)
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("true")
    })

    test("hides loading when nodes array is provided", () => {
        const wrapper = mount(KsGraph, {
            props: {nodes: [{id: "a", name: "a"}], loading: false},
        })
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("false")
    })

    test("loading prop true overrides node-based detection", () => {
        const wrapper = mount(KsGraph, {
            props: {nodes: [{id: "a", name: "a"}], loading: true},
        })
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("true")
    })

    test("loading prop false overrides null nodes", () => {
        const wrapper = mount(KsGraph, {props: {nodes: null, loading: false}})
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("false")
    })

    test("clears loading when nodes prop changes from null to array", async () => {
        const wrapper = mount(KsGraph, {props: {nodes: null}})
        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("true")

        await wrapper.setProps({nodes: [{id: "a", name: "a"}], loading: false})
        await nextTick()

        expect(wrapper.find(".ks-chart--graph").attributes("data-loading")).toBe("false")
    })
})

// ─── Series option ────────────────────────────────────────────────────────────

describe("KsGraph — series", () => {
    test("series type is graph", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        expect(getSeries(wrapper).type).toBe("graph")
    })

    test("default layout is force", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        expect(getSeries(wrapper).layout).toBe("force")
    })

    test("layout prop is forwarded to series", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false, layout: "circular"}})
        expect(getSeries(wrapper).layout).toBe("circular")
    })

    test("nodes are passed to series data", () => {
        const nodes = [{id: "n1", name: "flow-a"}, {id: "n2", name: "flow-b"}]
        const wrapper = mount(KsGraph, {props: {nodes, loading: false}})
        const data = getSeries(wrapper).data as unknown[]
        expect(data).toHaveLength(2)
    })

    test("edges are passed to series links", () => {
        const nodes = [{id: "n1", name: "a"}, {id: "n2", name: "b"}]
        const edges = [{source: "n1", target: "n2"}]
        const wrapper = mount(KsGraph, {props: {nodes, edges, loading: false}})
        const links = getSeries(wrapper).links as unknown[]
        expect(links).toHaveLength(1)
    })

    test("empty edges fallback when not provided", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        expect(getSeries(wrapper).links).toEqual([])
    })

    test("roam prop is forwarded", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false, roam: false}})
        expect(getSeries(wrapper).roam).toBe(false)
    })

    test("default roam is true", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        expect(getSeries(wrapper).roam).toBe(true)
    })

    test("edgeSymbol uses arrow target", () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        expect(getSeries(wrapper).edgeSymbol).toEqual(["none", "arrow"])
    })
})

// ─── Options deep merge ────────────────────────────────────────────────────────

describe("KsGraph — options deep merge", () => {
    test("user options override base option fields", () => {
        const wrapper = mount(KsGraph, {
            props: {
                nodes: [],
                loading: false,
                options: {series: [{force: {repulsion: 999}}]},
            },
        })
        const force = (getSeries(wrapper).force as Record<string, unknown>)
        expect(force.repulsion).toBe(999)
    })

    test("partial override preserves unspecified fields", () => {
        const wrapper = mount(KsGraph, {
            props: {
                nodes: [],
                loading: false,
                options: {series: [{roam: "move"}]},
            },
        })
        const series = getSeries(wrapper)
        expect(series.roam).toBe("move")
        expect(series.type).toBe("graph")
    })
})

