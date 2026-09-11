import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {nextTick} from "vue"
import {createI18n} from "vue-i18n"
import {useVueFlow} from "@vue-flow/core"
import Topology from "../../src/Topology.vue"
import type {FlowGraph} from "../../src/utils/vueFlowUtils"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

const FLOW_GRAPH: FlowGraph = {
    nodes: [
        {uid: "1", type: "task", task: {id: "task1", type: "io.kestra.plugin.core.log.Log", namespace: "io.kestra.tests", flowId: "flow"}},
        {uid: "2", type: "task", task: {id: "task2", type: "io.kestra.plugin.core.log.Log", namespace: "io.kestra.tests", flowId: "flow"}},
    ],
    edges: [{source: "1", target: "2"}],
    clusters: [],
}

function mountTopology(id: string) {
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
        const fitView = vi.spyOn(vueFlow, "fitView").mockResolvedValue(true)

        mountTopology(id)
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not recentre the view when the graph data is refreshed", async () => {
        const id = `topology-${Math.random()}`
        const vueFlow = useVueFlow(id)
        const fitView = vi.spyOn(vueFlow, "fitView").mockResolvedValue(true)

        const wrapper = mountTopology(id)
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
        const fitView = vi.spyOn(vueFlow, "fitView").mockResolvedValue(true)

        const wrapper = mountTopology(id)
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
        const fitView = vi.spyOn(vueFlow, "fitView").mockResolvedValue(true)

        const wrapper = mountTopology(id)
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({isHorizontal: false})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(2)
    })
})
