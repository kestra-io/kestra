import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {useVueFlow, type VueFlowInstance} from "@xyflow/vue"
import Topology from "./Topology.vue"
import {NODE_DETAILS_PEEK} from "./utils/constants"
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

const FLOW_GRAPH: FlowGraph = {
    nodes: [
        {uid: "1", type: "task", task: {id: "task1", type: "io.kestra.plugin.core.log.Log", namespace: "io.kestra.tests", flowId: "flow"}},
        {uid: "2", type: "task", task: {id: "task2", type: "io.kestra.plugin.core.log.Log", namespace: "io.kestra.tests", flowId: "flow"}},
    ],
    edges: [{source: "1", target: "2"}],
    clusters: [],
}

// Topology owns its store, so a test reaches it from where `<VueFlow>` sits: inside Topology.
function vueFlowProbe({zoom, rendersContent = false}: {zoom?: number; rendersContent?: boolean} = {}) {
    const probe = {} as {vueFlow: VueFlowInstance; fitView: ReturnType<typeof vi.fn>}
    const VueFlow = defineComponent({
        setup(_, {slots}) {
            probe.vueFlow = useVueFlow()
            probe.fitView = vi.spyOn(probe.vueFlow, "fitView").mockResolvedValue(true)
            if (zoom !== undefined) probe.vueFlow.setState({transform: [0, 0, zoom]})
            return () => h("div", rendersContent ? slots.default?.() : undefined)
        },
    })
    return {probe, VueFlow}
}

function mountForRefit() {
    const {probe, VueFlow} = vueFlowProbe()
    const wrapper = i18nMount(Topology, {
        props: {
            id: "vfid",
            flowGraph: FLOW_GRAPH,
            source: "",
        },
        global: {stubs: {VueFlow}},
    })
    return {wrapper, ...probe}
}

async function settle() {
    await nextTick()
    await nextTick()
    await nextTick()
}

describe("Topology view refit", () => {
    it("should fit the view once on the initial render", async () => {
        const {vueFlow, fitView} = mountForRefit()
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not recentre the view when the graph data is refreshed", async () => {
        const {wrapper, vueFlow, fitView} = mountForRefit()
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({flowGraph: {...FLOW_GRAPH, nodes: [...FLOW_GRAPH.nodes]}})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should not recentre the view when live task progress bumps taskDetailsVersion", async () => {
        const {wrapper, vueFlow, fitView} = mountForRefit()
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({taskDetailsVersion: 1})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(1)
    })

    it("should refit when a subflow expansion reloads the graph", async () => {
        const {wrapper, vueFlow, fitView} = mountForRefit()
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({expandedSubflows: ["1"]})
        await wrapper.setProps({flowGraph: {...FLOW_GRAPH, nodes: [...FLOW_GRAPH.nodes]}})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(2)
    })

    describe("when the panel is resized", () => {
        let resize: (width: number, height: number) => void

        beforeEach(() => {
            vi.useFakeTimers()
            vi.stubGlobal("ResizeObserver", class {
                constructor(callback: ResizeObserverCallback) {
                    resize = (width, height) => callback([{contentRect: {width, height}} as ResizeObserverEntry], this as unknown as ResizeObserver)
                }
                observe() {}
                unobserve() {}
                disconnect() {}
            })
        })

        afterEach(() => {
            vi.useRealTimers()
            vi.unstubAllGlobals()
        })

        async function mountFitted() {
            const {wrapper, vueFlow, fitView} = mountForRefit()
            await settle()
            vueFlow.emits.nodesInitialized([])
            await settle()
            resize(800, 600)
            expect(fitView).toHaveBeenCalledTimes(1)
            return {vueFlow, fitView, wrapper}
        }

        it("should refit the graph to the new size", async () => {
            const {fitView} = await mountFitted()

            resize(500, 600)
            vi.runAllTimers()

            expect(fitView).toHaveBeenCalledTimes(2)
        })

        it("should refit again once a layout change fits a moved view", async () => {
            const {vueFlow, fitView, wrapper} = await mountFitted()

            vueFlow.setState({transform: [120, 40, 2]})
            await wrapper.setProps({isHorizontal: true})
            await settle()
            vueFlow.emits.nodesInitialized([])
            await settle()
            resize(500, 600)
            vi.runAllTimers()

            expect(fitView).toHaveBeenCalledTimes(3)
        })
    })

    it("should refit when the orientation is toggled explicitly", async () => {
        const {wrapper, vueFlow, fitView} = mountForRefit()
        await settle()
        vueFlow.emits.nodesInitialized([])
        expect(fitView).toHaveBeenCalledTimes(1)

        await wrapper.setProps({isHorizontal: true})
        await settle()
        vueFlow.emits.nodesInitialized([])

        expect(fitView).toHaveBeenCalledTimes(2)
    })
})

describe("Topology task focus", () => {
    it("should fit the view on the node of the focused task", async () => {
        const {wrapper, fitView} = mountForRefit()
        await settle()
        wrapper.vm.focusTask("2")

        expect(fitView).toHaveBeenCalledWith(expect.objectContaining({nodes: [{id: "2"}]}))
    })
})

describe("Topology details peek", () => {
    function mountWithViewportZoom(zoom: number) {
        const {probe, VueFlow} = vueFlowProbe({zoom, rendersContent: true})
        const wrapper = i18nMount(Topology, {
            props: {id: "peek", source: "", flowGraph: FLOW_GRAPH},
            slots: {taskDetails: "<div class='peek-marker'>details</div>"},
            global: {stubs: {VueFlow, Background: true, Panel: true, Controls: true, ControlButton: true}},
        })
        return {wrapper, ...probe}
    }

    const ZOOM_ABOVE_PEEK_THRESHOLD = 1.5
    const ZOOM_BELOW_PEEK_THRESHOLD = 1

    const mountWithPeek = () => mountWithViewportZoom(ZOOM_ABOVE_PEEK_THRESHOLD).wrapper

    const PEEK = "[data-test=\"node-details-peek\"]"

    it("should open the peek for a hovered task only once the hover intent delay has passed", async () => {
        vi.useFakeTimers()
        try {
            const wrapper = mountWithPeek()
            await settle()

            wrapper.vm.onMouseOver({uid: "1"})
            vi.advanceTimersByTime(NODE_DETAILS_PEEK.HOVER_INTENT_MS - 50)
            await nextTick()
            expect(wrapper.find(PEEK).exists()).toBe(false)

            vi.advanceTimersByTime(100)
            await nextTick()
            expect(wrapper.find(PEEK).exists()).toBe(true)
            expect(wrapper.find("[data-test=\"node-details-peek-title\"]").text()).toBe("1")
            expect(wrapper.find(".peek-marker").exists()).toBe(true)
        } finally {
            vi.useRealTimers()
        }
    })

    it("should close an open peek on Escape", async () => {
        vi.useFakeTimers()
        try {
            const wrapper = mountWithPeek()
            await settle()

            wrapper.vm.onMouseOver({uid: "1"})
            vi.advanceTimersByTime(NODE_DETAILS_PEEK.HOVER_INTENT_MS)
            await nextTick()
            expect(wrapper.find(PEEK).exists()).toBe(true)

            window.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape"}))
            await nextTick()

            expect(wrapper.find(PEEK).exists()).toBe(false)
        } finally {
            vi.useRealTimers()
        }
    })

    it("should pin and unpin the peek when a read-only task card is clicked", async () => {
        const wrapper = mountWithPeek()
        await settle()

        wrapper.vm.onTaskCardClick({uid: "2"})
        await nextTick()
        expect(wrapper.find("[data-test=\"node-details-peek-title\"]").text()).toBe("2")

        wrapper.vm.onTaskCardClick({uid: "2"})
        await nextTick()
        expect(wrapper.find(PEEK).exists()).toBe(false)
    })

    it("should drop a pin taken while zoomed out instead of reopening it on the next zoom-in", async () => {
        const {wrapper, vueFlow} = mountWithViewportZoom(ZOOM_BELOW_PEEK_THRESHOLD)
        await settle()

        wrapper.vm.onTaskCardClick({uid: "2"})
        await nextTick()
        expect(wrapper.find(PEEK).exists()).toBe(false)

        vueFlow.setState({transform: [0, 0, ZOOM_ABOVE_PEEK_THRESHOLD]})
        await nextTick()

        expect(wrapper.find(PEEK).exists()).toBe(false)
    })

    it("should keep an open peek through the empty gap of a graph regeneration", async () => {
        vi.useFakeTimers()
        try {
            const {wrapper, vueFlow} = mountWithViewportZoom(ZOOM_ABOVE_PEEK_THRESHOLD)
            await settle()

            wrapper.vm.onMouseOver({uid: "1"})
            vi.advanceTimersByTime(NODE_DETAILS_PEEK.HOVER_INTENT_MS)
            await nextTick()
            expect(wrapper.find(PEEK).exists()).toBe(true)

            vueFlow.setNodes([])
            await nextTick()

            expect(wrapper.find(PEEK).exists()).toBe(true)
            expect(wrapper.find("[data-test=\"node-details-peek-title\"]").text()).toBe("1")
        } finally {
            vi.useRealTimers()
        }
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

    it("offers nothing to collapse when the API returns no clusters", () => {
        const wrapper = mountWithControls({nodes: graph.nodes, edges: graph.edges})

        expect(wrapper.find("[data-test='topology-collapse-all']").exists()).toBe(false)
    })
})
