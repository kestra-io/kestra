import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import KsGraph from "../../../src/components/Charts/KsGraph.vue"
import type {KsGraphNode, KsGraphEdge} from "../../../src/components/Charts/KsGraph.vue"
import {ChartRenderer} from "../../../src"

// ─── Fixture helpers ──────────────────────────────────────────────────────────

function makeNode(id: string, name: string, size = 20, color?: string): KsGraphNode {
    return {
        id,
        name,
        symbolSize: size,
        ...(color ? {itemStyle: {color}} : {}),
    }
}

function makeEdge(source: string, target: string): KsGraphEdge {
    return {source, target}
}

// npm-style dependency graph (mirrors the ECharts example)
const NPM_NODES: KsGraphNode[] = [
    makeNode("express",   "express",   60, "#5470c6"),
    makeNode("qs",        "qs",        30, "#91cc75"),
    makeNode("path",      "path",      25, "#fac858"),
    makeNode("debug",     "debug",     25, "#ee6666"),
    makeNode("cookie",    "cookie",    20, "#73c0de"),
    makeNode("send",      "send",      30, "#3ba272"),
    makeNode("serve-static", "serve-static", 28, "#fc8452"),
    makeNode("utils",     "utils",     20, "#9a60b4"),
    makeNode("mime",      "mime",      18, "#ea7ccc"),
    makeNode("fresh",     "fresh",     15),
    makeNode("range-parser", "range-parser", 15),
    makeNode("etag",      "etag",      15),
]

const NPM_EDGES: KsGraphEdge[] = [
    makeEdge("express", "qs"),
    makeEdge("express", "path"),
    makeEdge("express", "debug"),
    makeEdge("express", "send"),
    makeEdge("express", "serve-static"),
    makeEdge("express", "utils"),
    makeEdge("send", "cookie"),
    makeEdge("send", "mime"),
    makeEdge("send", "fresh"),
    makeEdge("send", "range-parser"),
    makeEdge("send", "etag"),
    makeEdge("serve-static", "send"),
    makeEdge("debug", "utils"),
]

// Simple dependency graph (Kestra flow-like)
const FLOW_NODES: KsGraphNode[] = [
    makeNode("a::n1", "ingestion", 40, "#5470c6"),
    makeNode("a::n2", "transform",  30, "#91cc75"),
    makeNode("a::n3", "validate",   25, "#fac858"),
    makeNode("b::n4", "alert",      20, "#ee6666"),
    makeNode("b::n5", "report",     20, "#73c0de"),
]

const FLOW_EDGES: KsGraphEdge[] = [
    makeEdge("a::n1", "a::n2"),
    makeEdge("a::n2", "a::n3"),
    makeEdge("a::n3", "b::n4"),
    makeEdge("a::n3", "b::n5"),
]

// ─── Meta ─────────────────────────────────────────────────────────────────────

const meta: Meta<typeof KsGraph> = {
    title: "Components/Charts/KsGraph",
    component: KsGraph,
    tags: ["autodocs"],
    argTypes: {
        loading:  {control: "boolean"},
        layout:   {control: "select", options: ["force", "circular", "none"]},
        roam:     {control: "boolean"},
        renderer: {control: "select", options: ["canvas", "svg"]},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsGraph renders a force-directed (or circular) node-link graph powered by ECharts. " +
                    "Pass `nodes` as an array of `{id, name, symbolSize?, itemStyle?, ...}` objects and " +
                    "`edges` as `{source, target, lineStyle?, ...}` pairs. " +
                    "Set `nodes` to `null` while fetching to show the built-in loading indicator. " +
                    "Expose methods `zoomIn()`, `zoomOut()`, `fit()`, and `exportAsImage()` are available " +
                    "via a template ref.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsGraph>

// ─── Stories ──────────────────────────────────────────────────────────────────

/** NPM-style dependency graph — mirrors the official ECharts graph-npm example */
export const Default: Story = {
    render: (args) => ({
        components: {KsGraph},
        setup() { return {args} },
        template: "<div style=\"padding:24px;height:480px\"><ks-graph v-bind=\"args\" /></div>",
    }),
    args: {
        nodes: NPM_NODES,
        edges: NPM_EDGES,
        loading: false,
        layout: "force",
        roam: true,
    },
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--graph")).toBeTruthy()
    },
}

/** Circular layout — evenly-spaced ring */
export const CircularLayout: Story = {
    render: () => ({
        components: {KsGraph},
        setup() {
            return {nodes: NPM_NODES, edges: NPM_EDGES}
        },
        template: `
            <div style="padding:24px;height:480px">
                <ks-graph
                    layout="circular"
                    :nodes="nodes"
                    :edges="edges"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Loading state — shown while data is being fetched */
export const Loading: Story = {
    render: () => ({
        components: {KsGraph},
        template: "<div style=\"padding:24px;height:480px\"><ks-graph :nodes=\"null\" /></div>",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--graph")).toBeTruthy()
    },
}

/** Selected-node visual state — pre-style nodes to show which one is active */
export const WithSelection: Story = {
    render: () => ({
        components: {KsGraph},
        setup() {
            const SELECTED_COLOR  = "#5470c6"
            const NEIGHBOR_COLOR  = "#91cc75"
            const FADED_OPACITY   = 0.25

            const selectedId = "a::n1"
            const neighborIds = new Set(
                FLOW_EDGES
                    .filter(e => e.source === selectedId || e.target === selectedId)
                    .flatMap(e => [e.source, e.target]),
            )

            const nodes: KsGraphNode[] = FLOW_NODES.map(n => ({
                ...n,
                itemStyle: {
                    color: n.id === selectedId
                        ? SELECTED_COLOR
                        : neighborIds.has(n.id)
                            ? NEIGHBOR_COLOR
                            : "#aaa",
                    opacity: neighborIds.has(n.id) || n.id === selectedId ? 1 : FADED_OPACITY,
                },
            }))

            const edges: KsGraphEdge[] = FLOW_EDGES.map(e => ({
                ...e,
                lineStyle: {
                    color: e.source === selectedId || e.target === selectedId
                        ? SELECTED_COLOR
                        : "#ccc",
                    type: e.source === selectedId || e.target === selectedId
                        ? "dashed"
                        : "solid",
                    opacity: e.source === selectedId || e.target === selectedId ? 1 : 0.2,
                },
            }))

            return {nodes, edges}
        },
        template: `
            <div style="padding:24px;height:400px">
                <ks-graph :nodes="nodes" :edges="edges" :loading="false" />
            </div>
        `,
    }),
}

/** SVG renderer — useful for print / high-DPI exports */
export const SvgRenderer: Story = {
    render: () => ({
        components: {KsGraph},
        setup() {
            return {nodes: FLOW_NODES, edges: FLOW_EDGES, renderer: ChartRenderer.SVG}
        },
        template: `
            <div style="padding:24px;height:480px">
                <ks-graph
                    :renderer="renderer"
                    :nodes="nodes"
                    :edges="edges"
                    :loading="false"
                />
            </div>
        `,
    }),
}

/** Options override — custom force repulsion and edge curveness */
export const WithOptionsOverride: Story = {
    render: () => ({
        components: {KsGraph},
        setup() {
            const options = {
                series: [{
                    force: {repulsion: 1200, edgeLength: 120},
                    lineStyle: {curveness: 0.4},
                }],
            }
            return {nodes: NPM_NODES, edges: NPM_EDGES, options}
        },
        template: `
            <div style="padding:24px;height:480px">
                <ks-graph
                    :nodes="nodes"
                    :edges="edges"
                    :loading="false"
                    :options="options"
                />
            </div>
        `,
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".ks-chart--graph")).toBeTruthy()
    },
}
