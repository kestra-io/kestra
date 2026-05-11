// Event-handling tests for KsGraph.
// Kept in a separate file because vi.mock is hoisted to file scope, so two
// different mocks for the same module cannot coexist in one file.
import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import KsGraph from "../../../src/components/Charts/KsGraph.vue"

vi.mock("echarts/core", () => ({use: vi.fn()}))
vi.mock("echarts/charts", () => ({GraphChart: {}}))

// ─── Fake ECharts instance ────────────────────────────────────────────────────
// Tracks registered listeners so tests can simulate ECharts events.
const listeners: Record<string, ((params: unknown) => void)[]> = {}

const fakeChart = {
    on(event: string, handler: (params: unknown) => void) {
        listeners[event] = listeners[event] ?? []
        listeners[event].push(handler)
    },
    setOption: vi.fn(),
    getOption: vi.fn(() => ({series: [{zoom: 1}]})),
}

function fire(event: string, params: unknown) {
    listeners[event]?.forEach(h => h(params))
}

vi.mock("../../../src/components/Charts/KsEchart.vue", () => ({
    default: {
        name: "KsEchart",
        props: ["options", "loading", "tooltipType", "disableFeatures", "data", "renderer"],
        template: "<div class=\"ks-chart--graph\" />",
        methods: {
            getEchartsInstance() { return fakeChart },
            exportAsImage: vi.fn(),
        },
    },
}))

beforeEach(() => {
    Object.keys(listeners).forEach(k => { listeners[k] = [] })
    fakeChart.setOption.mockClear()
})

describe("KsGraph — events", () => {
    test("mouseover on a node emits node-hover with the node data", async () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        await nextTick()

        const node = {id: "A", name: "A"}
        fire("mouseover", {dataType: "node", data: node})

        const emitted = wrapper.emitted("node-hover")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual(node)
    })

    test("mouseout on a node emits node-hover with null", async () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        await nextTick()

        fire("mouseout", {dataType: "node", data: {id: "A", name: "A"}})

        const emitted = wrapper.emitted("node-hover")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toBeNull()
    })

    test("mouseover on an edge does not emit node-hover", async () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        await nextTick()

        fire("mouseover", {dataType: "edge", data: {}})

        expect(wrapper.emitted("node-hover")).toBeFalsy()
    })

    test("click on a node emits node-click with the node data", async () => {
        const wrapper = mount(KsGraph, {props: {nodes: [], loading: false}})
        await nextTick()

        const node = {id: "B", name: "B"}
        fire("click", {dataType: "node", data: node})

        const emitted = wrapper.emitted("node-click")
        expect(emitted).toBeTruthy()
        expect(emitted![0][0]).toEqual(node)
    })
})
