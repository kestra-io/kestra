import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {flow as fetchFlow, generateFlowGraph, generateFlowGraphFromSource, validateFlows} from "@kestra-io/kestra-sdk/flows"
import {useFlowStore, type Flow} from "./flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: undefined}),
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    validateFlows: vi.fn(() => Promise.resolve([{}])),
    generateFlowGraphFromSource: vi.fn(),
    generateFlowGraph: vi.fn(),
    flow: vi.fn(),
}))
const PLUGIN_FLOWABLE = "io.kestra.plugin.acme.Retry"
vi.mock("./plugins", () => ({
    usePluginsStore: () => ({
        iconsLoaded: true,
        icons: {"io.kestra.plugin.acme.Retry": {flowable: true}},
        fetchIcons: vi.fn(),
    }),
}))

const flowWith = (task: string) => `id: hello\nnamespace: company.team\ntasks:\n${task}\n`
const LOG = "  - id: log\n    type: io.kestra.plugin.core.log.Log"
const BACKEND_GRAPH = {nodes: [{uid: "root.from-backend", type: "GraphTask"}], edges: [], clusters: []}

const taskUids = (store: ReturnType<typeof useFlowStore>) =>
    store.flowGraph?.nodes.filter((node) => node.task).map((node) => node.uid)

describe("flow store topology", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.mocked(generateFlowGraphFromSource).mockReset().mockResolvedValue(BACKEND_GRAPH)
        vi.mocked(validateFlows).mockReset().mockResolvedValue([{index: 0}])
        vi.mocked(generateFlowGraph).mockReset()
        vi.mocked(fetchFlow).mockReset().mockRejectedValue({response: {status: 404}})
    })

    it("should draw the graph from the source without asking the backend", async () => {
        const store = useFlowStore()

        await store.loadGraphFromSource({flow: flowWith(LOG)})

        expect(taskUids(store)).toEqual(["root.log"])
        expect(store.invalidGraph).toBe(false)
        expect(generateFlowGraphFromSource).not.toHaveBeenCalled()
    })

    it("should ask the backend when a subflow is expanded", async () => {
        const store = useFlowStore()

        await store.loadGraphFromSource({flow: flowWith(LOG), config: {params: {subflows: "root.log"}}})

        expect(generateFlowGraphFromSource).toHaveBeenCalledWith({subflows: ["root.log"], body: flowWith(LOG)})
        expect(store.flowGraph?.nodes[0].uid).toBe("root.from-backend")
    })

    it("should ask the backend when the flow uses a flowable shipped by a plugin", async () => {
        const store = useFlowStore()

        const graph = await store.getGraphFromSourceResponse({flow: flowWith(`  - id: retry\n    type: ${PLUGIN_FLOWABLE}`)})

        expect(generateFlowGraphFromSource).toHaveBeenCalledOnce()
        expect(graph.nodes[0].uid).toBe("root.from-backend")
    })

    it("should keep the last graph and flag it when the source cannot be drawn", async () => {
        const store = useFlowStore()
        await store.loadGraphFromSource({flow: flowWith(LOG)})

        await store.loadGraphFromSource({flow: "tasks: [unclosed"})

        expect(taskUids(store)).toEqual(["root.log"])
        expect(store.invalidGraph).toBe(true)
        expect(generateFlowGraphFromSource).not.toHaveBeenCalled()
    })

    it("should keep the server state of a trigger on the graph it builds", async () => {
        const store = useFlowStore()
        const source = `${flowWith(LOG)}triggers:\n  - id: nightly\n    type: io.kestra.plugin.core.trigger.Schedule\n`
        const savedGraph = {nodes: [{uid: "root.Triggers.nightly", type: "GraphTrigger", trigger: {disabled: true}}]}
        vi.mocked(generateFlowGraph).mockResolvedValue(savedGraph)
        await store.loadGraph({flow: {id: "hello", namespace: "company.team"} as Flow})

        await store.loadGraphFromSource({flow: source})

        const trigger = store.flowGraph?.nodes.find((node) => node.uid === "root.Triggers.nightly")
        expect(trigger?.trigger?.disabled).toBe(true)
        expect(generateFlowGraphFromSource).not.toHaveBeenCalled()
    })

    it("should disable a subflow node whose flow is disabled, asking for that flow once", async () => {
        const store = useFlowStore()
        const source = flowWith("  - id: child\n    type: io.kestra.plugin.core.flow.Subflow\n    namespace: company.team\n    flowId: archived")
        const archived = {id: "archived", namespace: "company.team", disabled: true, draft: false, deleted: false, tasks: []}
        vi.mocked(fetchFlow).mockResolvedValue(archived)

        await store.loadGraphFromSource({flow: source})
        await store.loadGraphFromSource({flow: source})

        expect(store.flowGraph?.nodes.find((node) => node.uid === "root.child")?.disabled).toBe(true)
        expect(fetchFlow).toHaveBeenCalledOnce()
        expect(fetchFlow).toHaveBeenCalledWith({namespace: "company.team", id: "archived", revision: undefined})
    })

    it("should redraw on edit when the validation never answers", async () => {
        const store = useFlowStore()
        store.flow = {id: "hello", namespace: "company.team"} as Flow
        store.flowYaml = flowWith("  - id: edited\n    type: io.kestra.plugin.core.log.Log")
        vi.mocked(validateFlows).mockRejectedValue(new Error("Network Error"))

        await expect(store.onEdit({source: store.flowYaml, topologyVisible: true})).rejects.toThrow("Network Error")

        expect(taskUids(store)).toEqual(["root.edited"])
    })
})
