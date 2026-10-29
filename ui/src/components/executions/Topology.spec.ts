import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

vi.mock("../inputs/LowCodeEditor.vue", async () => {
    const {defineComponent} = await import("vue")
    return {default: defineComponent({name: "LowCodeEditorStub", emits: ["expand-subflow"], template: "<div data-test='low-code-editor' />"})}
})

const {useExecutionsStore} = await import("../../stores/executions")
const {useFlowStore} = await import("../../stores/flow")
const Topology = (await import("./Topology.vue")).default

const LOOP_TYPE = "io.kestra.plugin.core.flow.Loop"

const flowGraph = {
    nodes: [
        {uid: "per_region", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "per_region", type: LOOP_TYPE}},
        {uid: "per_region.call", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "call", type: "io.kestra.plugin.core.flow.Subflow"}},
    ],
    edges: [],
    clusters: [{cluster: {uid: "cluster_per_region", type: "io.kestra.core.models.hierarchies.GraphCluster", taskNode: {uid: "per_region", task: {type: LOOP_TYPE}}}}],
}

const iteration = (id: string) => ({
    id,
    state: {current: "SUCCESS"},
    taskRunList: [{id: `${id}-call`, executionId: "root", taskId: "call", state: {current: "RUNNING"}}],
})

const child = (id: string, taskRunId: string) => ({id, trigger: {variables: {taskRunId}}})

type Setup = {taskRunList?: unknown[]; graph?: unknown; children?: ReturnType<typeof child>[]}

function setup({taskRunList = [{id: "loop-run", taskId: "per_region", state: {current: "SUCCESS"}}], graph = flowGraph, children = []}: Setup = {}) {
    const store = useExecutionsStore()
    store.execution = {id: "root", namespace: "ns", flowId: "flow", state: {current: "SUCCESS"}, taskRunList} as never
    store.flowGraph = graph as never
    store.loadAugmentedGraph = vi.fn().mockResolvedValue(graph) as never
    store.findExecutions = vi.fn(() => Promise.resolve({results: children, total: children.length})) as never
    const subscriptions: {executionId: string; close: ReturnType<typeof vi.fn>}[] = []
    store.subscribeToExecution = vi.fn((executionId: string) => {
        const subscription = {executionId, close: vi.fn()}
        subscriptions.push(subscription)
        return subscription
    }) as never
    const flowStore = useFlowStore()
    flowStore.loadFlow = vi.fn() as never

    const wrapper = i18nMount(Topology)
    const expand = (subflows: string[]) => wrapper.findComponent({name: "LowCodeEditorStub"}).vm.$emit("expand-subflow", subflows)
    return {store, subscriptions, wrapper, expand}
}

describe("executions/Topology subflow expanded inside a loop", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("shouldNotFollowAnyExecutionNorRetryWhileTheLoopIsUnscoped", async () => {
        const {store, expand} = setup()
        await flushPromises()

        expand(["per_region.call"])
        await flushPromises()
        await vi.advanceTimersByTimeAsync(5_000)

        expect(store.subscribeToExecution).not.toHaveBeenCalled()
        expect(vi.getTimerCount()).toBe(0)
    })

    it("shouldFollowTheSubflowRunOfTheScopedIterationAndSwitchWithTheScope", async () => {
        const {store, subscriptions, expand} = setup()
        await flushPromises()
        expand(["per_region.call"])
        await flushPromises()

        vi.mocked(store.findExecutions).mockResolvedValue({results: [child("child-0", "it-0-call"), child("child-1", "it-1-call")], total: 2} as never)

        store.setScopedIterations({per_region: iteration("it-0")} as never)
        await flushPromises()
        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child-0"])

        store.setScopedIterations({per_region: iteration("it-1")} as never)
        await flushPromises()

        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child-0", "child-1"])
        expect(subscriptions[0].close).toHaveBeenCalled()
    })
})

describe("executions/Topology expanded subflow", () => {
    const subflowGraph = {
        nodes: [{uid: "call_child", type: "io.kestra.core.models.hierarchies.GraphTask", task: {id: "call_child", type: "io.kestra.plugin.core.flow.Subflow"}}],
        edges: [],
        clusters: [],
    }
    const subflowRun = (state: string) => ({id: "call-run", executionId: "root", taskId: "call_child", state: {current: state, startDate: "2026-10-28T10:00:00Z"}})

    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it.each(["SUCCESS", "FAILED", "RUNNING"])("shouldFollowTheChildExecutionTriggeredByTheSubflowRunWhenIt%s", async (state) => {
        const {store, subscriptions, expand} = setup({
            taskRunList: [subflowRun(state)],
            graph: subflowGraph,
            children: [child("other-child", "other-run"), child("child", "call-run")],
        })
        await flushPromises()

        expand(["call_child"])
        await flushPromises()

        expect(store.findExecutions).toHaveBeenCalledWith(expect.objectContaining({
            "filters[triggerExecutionId][EQUALS]": "root",
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-10-28T10:00:00Z",
            commit: false,
        }))
        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child"])
    })

    it("shouldRetryUntilTheChildExecutionOfARunningSubflowIsFound", async () => {
        const {store, subscriptions, expand} = setup({taskRunList: [subflowRun("RUNNING")], graph: subflowGraph})
        await flushPromises()

        expand(["call_child"])
        await flushPromises()
        expect(store.subscribeToExecution).not.toHaveBeenCalled()

        vi.mocked(store.findExecutions).mockResolvedValue({results: [child("child", "call-run")], total: 1} as never)
        await vi.advanceTimersByTimeAsync(500)

        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child"])
    })

    it("shouldStopLookingWhenAFinishedSubflowHasNoChildExecution", async () => {
        const {store, expand} = setup({taskRunList: [subflowRun("SUCCESS")], graph: subflowGraph})
        await flushPromises()

        expand(["call_child"])
        await flushPromises()
        await vi.advanceTimersByTimeAsync(5_000)

        expect(store.findExecutions).toHaveBeenCalledTimes(1)
        expect(store.subscribeToExecution).not.toHaveBeenCalled()
    })
})
