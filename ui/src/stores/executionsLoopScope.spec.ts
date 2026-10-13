import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

const {executionFlowGraphMock} = vi.hoisted(() => ({executionFlowGraphMock: vi.fn()}))
vi.mock("@kestra-io/kestra-sdk/executions", () => ({executionFlowGraph: executionFlowGraphMock}))

const {useExecutionsStore} = await import("./executions")

const graph = () => ({
    nodes: [
        {uid: "per_region", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "per_region", type: "io.kestra.plugin.core.flow.Loop"}},
        {uid: "per_region.upload", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "upload", type: "io.kestra.plugin.core.log.Log"}},
        {uid: "write_report", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "write_report", type: "io.kestra.plugin.core.log.Log"}},
    ],
    clusters: [],
    edges: [],
})

const executionIds = (store: ReturnType<typeof useExecutionsStore>) =>
    Object.fromEntries((store.flowGraph?.nodes ?? []).map((node) => [node.uid, node.executionId]))

describe("executions store loop scope", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        executionFlowGraphMock.mockResolvedValue(graph())
    })

    it("shouldPointNodesUnderAScopedLoopAtTheIterationExecution", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})

        store.addSubflowExecution({subflow: "per_region", execution: {id: "iteration-1"} as never})
        store.applyScopedExecutionIds()

        expect(executionIds(store)).toEqual({per_region: "main", "per_region.upload": "iteration-1", write_report: "main"})
    })

    it("shouldRestoreTheMainExecutionWhenTheScopeIsCleared", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})
        store.addSubflowExecution({subflow: "per_region", execution: {id: "iteration-1"} as never})
        store.applyScopedExecutionIds()

        store.removeSubflowExecution("per_region")
        store.applyScopedExecutionIds()

        expect(executionIds(store)["per_region.upload"]).toBe("main")
    })

    it("shouldKeepTheScopeWhenTheGraphIsReloaded", async () => {
        const store = useExecutionsStore()
        store.addSubflowExecution({subflow: "per_region", execution: {id: "iteration-1"} as never})

        await store.loadAugmentedGraph({id: "main"})

        expect(executionIds(store)["per_region.upload"]).toBe("iteration-1")
    })
})
