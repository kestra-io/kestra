import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import {createI18n} from "vue-i18n"
import {useVueFlow} from "@vue-flow/core"
import Topology from "../../src/Topology.vue"
import type {FlowGraph} from "../../src/utils/vueFlowUtils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

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

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

const FLOW_GRAPH = {
    nodes: [
        {uid: "1", type: "task", task: {id: "task1", type: "io.kestra.plugin.core.log.Log"}},
        {uid: "2", type: "task", task: {id: "task2", type: "io.kestra.plugin.core.log.Log"}},
    ],
    edges: [{source: "1", target: "2", id: "e1", type: "default"}],
    clusters: [],
} as any

function mountForRefit(id: string) {
    return mount(Topology, {
        props: {
            id,
            flowGraph: FLOW_GRAPH,
            source: "",
        },
        global: {
            plugins: [i18n],
            // VueFlow's own scoped slots (node-cluster, node-task, ...) are only invoked by its real
            // implementation - stubbing it as an empty shell keeps this a test of Topology's own
            // refit logic without needing to render the graph nodes or vue-flow's controls/panels.
            stubs: {
                VueFlow: {template: "<div></div>"},
            },
        },
    })
}

async function settle() {
    await nextTick()
    await nextTick()
    await nextTick()
}

describe("Topology view refit", () => {
    it("should fit the view once on the initial render", async () => {
        const id = `topology-${Math.random()}`
        const vueFlow = useVueFlow(id)
        const fitView = vi.fn()
        vueFlow.fitView = fitView

        mountForRefit(id)
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not recentre the view when the graph data is refreshed", async () => {
        const id = `topology-${Math.random()}`
        const vueFlow = useVueFlow(id)
        const fitView = vi.fn()
        vueFlow.fitView = fitView

        const wrapper = mountForRefit(id)
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({flowGraph: {...FLOW_GRAPH, nodes: [...FLOW_GRAPH.nodes]}})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not recentre the view when live task progress bumps taskDetailsVersion", async () => {
        const id = `topology-${Math.random()}`
        const vueFlow = useVueFlow(id)
        const fitView = vi.fn()
        vueFlow.fitView = fitView

        const wrapper = mountForRefit(id)
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({taskDetailsVersion: 1})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should refit when the orientation is toggled explicitly", async () => {
        const id = `topology-${Math.random()}`
        const vueFlow = useVueFlow(id)
        const fitView = vi.fn()
        vueFlow.fitView = fitView

        const wrapper = mountForRefit(id)
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({isHorizontal: true})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(2)
    })
})
