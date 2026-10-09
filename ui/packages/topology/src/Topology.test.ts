import {describe, expect, it} from "vitest"
import Topology from "./Topology.vue"
import type {FlowGraph} from "./utils/vueFlowUtils"

import {i18nMount} from "../../../tests/unit/i18nMount"

const EMPTY_GRAPH: FlowGraph = {nodes: [], edges: [], clusters: []}

function mountTopology(isReadOnly: boolean, isAllowedEdit: boolean) {
    return i18nMount(Topology, {
        props: {
            id: "vfid",
            source: "",
            flowGraph: EMPTY_GRAPH,
            flowId: "my_flow",
            namespace: "company.team",
            isReadOnly,
            isAllowedEdit,
        },
        global: {stubs: {VueFlow: true, Background: true, Panel: true, Controls: true, ControlButton: true}},
    })
}

describe("Topology flow bar", () => {
    // The chip is the only authoring affordance that is not behind a node, so it does not inherit
    // the gating the rest of the canvas gets from TaskNode. An execution's topology and a
    // blueprint preview both pass `flowId` while read-only.
    it.each([
        ["read-only", true, false],
        ["read-only but allowed to edit elsewhere", true, true],
        ["editable but not allowed", false, false],
    ])("renders no flow chip when %s", (_label, isReadOnly, isAllowedEdit) => {
        const wrapper = mountTopology(isReadOnly, isAllowedEdit)

        expect(wrapper.find("[data-test=\"topology-flow-chip\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"topology-flow-configure\"]").exists()).toBe(false)
    })

    it("renders the flow chip on an editable flow", () => {
        const wrapper = mountTopology(false, true)

        expect(wrapper.find("[data-test=\"topology-flow-chip\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"topology-flow-configure\"]").exists()).toBe(true)
    })
})

describe("Topology collapse all", () => {
    const PARALLEL = "io.kestra.plugin.core.flow.Parallel"
    const graph: FlowGraph = {
        nodes: [
            {uid: "parallel", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "parallel", type: PARALLEL}},
            {uid: "parallel.a", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "a", type: "io.kestra.plugin.core.log.Log"}},
        ],
        edges: [{source: "parallel", target: "parallel.a"}],
        clusters: [{
            cluster: {uid: "cluster_parallel", type: "io.kestra.core.models.hierarchies.GraphCluster", taskNode: {uid: "parallel", task: {type: PARALLEL}}},
            nodes: ["parallel", "parallel.a"],
            parents: [],
            start: "parallel",
            end: "parallel.a",
        }],
    }

    const mountWithControls = (flowGraph: FlowGraph) => i18nMount(Topology, {
        props: {id: "collapse-all", source: "", flowGraph, isReadOnly: false, isAllowedEdit: true},
        global: {
            stubs: {
                VueFlow: {template: "<div><slot /></div>"},
                Controls: {template: "<div><slot /></div>"},
                ControlButton: {template: "<button><slot /></button>"},
                Background: true,
                Panel: true,
            },
        },
    })

    it("offers collapse all in the flow editor when the graph has a flowable lane", async () => {
        const wrapper = mountWithControls(graph)

        expect(wrapper.find("[data-test='topology-collapse-all']").exists()).toBe(true)
        const before = wrapper.findAll("button").length
        await wrapper.find("[data-test='topology-collapse-all']").trigger("click")
        expect(wrapper.findAll("button").length).toBe(before + 1)
    })

    it("offers nothing to collapse when the graph has no flowable lane", () => {
        const wrapper = mountWithControls({nodes: [], edges: [], clusters: []})

        expect(wrapper.find("[data-test='topology-collapse-all']").exists()).toBe(false)
    })
})
