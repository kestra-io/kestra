import {describe, expect, it} from "vitest"
import Topology from "../../src/Topology.vue"

import {i18nMount} from "../../../../tests/unit/i18nMount"

const EMPTY_GRAPH = {nodes: [], edges: [], clusters: []} as any

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
