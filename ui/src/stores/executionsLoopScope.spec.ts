import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {nextTick} from "vue"

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

const executionIds = (graph: {nodes: {uid: string; executionId?: string}[]} | undefined) =>
    Object.fromEntries((graph?.nodes ?? []).map((node) => [node.uid, node.executionId]))

describe("executions store loop scope", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        executionFlowGraphMock.mockResolvedValue(graph())
    })

    it("shouldPointNodesUnderAScopedLoopAtTheIterationExecution", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})

        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        expect(executionIds(store.scopedFlowGraph)).toEqual({per_region: "main", "per_region.upload": "iteration-1", write_report: "main"})
    })

    it("shouldNotRewriteTheLoadedGraphWhenAnIterationIsScoped", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})

        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        expect(executionIds(store.flowGraph)).toEqual({per_region: "main", "per_region.upload": "main", write_report: "main"})
    })

    it("shouldRestoreTheMainExecutionWhenTheScopeIsCleared", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})
        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        store.setScopedIterations({})

        expect(executionIds(store.scopedFlowGraph)["per_region.upload"]).toBe("main")
    })

    it("shouldKeepTheScopeWhenTheGraphIsReloaded", async () => {
        const store = useExecutionsStore()
        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        await store.loadAugmentedGraph({id: "main"})

        expect(executionIds(store.scopedFlowGraph)["per_region.upload"]).toBe("iteration-1")
    })

    it("shouldDeriveBaseExecutionIdsFromTheLatestLoadedGraph", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})
        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        await store.loadAugmentedGraph({id: "other"})
        store.setScopedIterations({})

        expect(executionIds(store.scopedFlowGraph)).toEqual({per_region: "other", "per_region.upload": "other", write_report: "other"})
    })

    it("shouldKeepScopedIterationsAndSubflowExecutionsInSeparateSlices", () => {
        const store = useExecutionsStore()
        store.addSubflowExecution({subflow: "per_region", execution: {id: "subflow-1"} as never})

        store.setScopedIterations({per_region: {id: "iteration-1"} as never})
        store.setScopedIterations({})

        expect(store.subflowsExecutions.per_region.id).toBe("subflow-1")
    })

    it("shouldExposeBothSlicesToTheGraphKeyedByExecutionId", () => {
        const store = useExecutionsStore()
        store.addSubflowExecution({subflow: "per_region", execution: {id: "subflow-1"} as never})

        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        expect(Object.keys(store.graphExecutions).sort()).toEqual(["iteration-1", "subflow-1"])
    })

    it("shouldKeepTheSameGraphWhenAScopedExecutionUpdatesWithoutChangingId", async () => {
        const store = useExecutionsStore()
        await store.loadAugmentedGraph({id: "main"})
        store.setScopedIterations({per_region: {id: "iteration-1", state: {current: "RUNNING"}} as never})
        const before = store.scopedFlowGraph

        store.setScopedIterations({per_region: {id: "iteration-1", state: {current: "SUCCESS"}} as never})

        expect(store.scopedFlowGraph).toBe(before)
    })

    it("shouldNotFlagEdgesUnusedWhenTheScopedIterationRanTheirTasks", async () => {
        executionFlowGraphMock.mockResolvedValue({
            ...graph(),
            edges: [{source: "per_region.upload", target: "write_report"}],
        })
        const store = useExecutionsStore()
        store.execution = {id: "main", taskRunList: [{taskId: "write_report"}]} as never
        store.setScopedIterations({per_region: {id: "iteration-1", taskRunList: [{taskId: "upload"}]} as never})

        await store.loadAugmentedGraph({id: "main"})

        expect(store.flowGraph?.edges[0].unused).toBeFalsy()
    })

    it("shouldDropTheScopeWhenTheExecutionIsCleared", async () => {
        const store = useExecutionsStore()
        store.execution = {id: "main"} as never
        await nextTick()
        store.setScopedIterations({per_region: {id: "iteration-1"} as never})

        store.execution = undefined
        await nextTick()

        expect(store.scopedIterations).toEqual({})
    })
})
