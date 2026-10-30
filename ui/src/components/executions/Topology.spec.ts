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

const iteration = (id: string, childExecutionId: string) => ({
    id,
    state: {current: "SUCCESS"},
    taskRunList: [{id: `${id}-call`, taskId: "call", state: {current: "RUNNING"}, outputs: {executionId: childExecutionId}}],
})

function setup(loadFlow = vi.fn().mockResolvedValue(undefined)) {
    const store = useExecutionsStore()
    store.execution = {id: "root", namespace: "ns", flowId: "flow", state: {current: "SUCCESS"}, taskRunList: [{id: "loop-run", taskId: "per_region", state: {current: "SUCCESS"}}]} as never
    store.flowGraph = flowGraph as never
    store.loadAugmentedGraph = vi.fn().mockResolvedValue(flowGraph) as never
    const subscriptions: {executionId: string; close: ReturnType<typeof vi.fn>}[] = []
    store.subscribeToExecution = vi.fn((executionId: string) => {
        const subscription = {executionId, close: vi.fn()}
        subscriptions.push(subscription)
        return subscription
    }) as never
    const flowStore = useFlowStore()
    flowStore.loadFlow = loadFlow as never

    const wrapper = i18nMount(Topology)
    const expand = (subflows: string[]) => wrapper.findComponent({name: "LowCodeEditorStub"}).vm.$emit("expand-subflow", subflows)
    return {store, subscriptions, wrapper, expand}
}

describe("executions/Topology flow source", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("loads the flow source as optional, so a viewer without access to the flow sees no error", async () => {
        const loadFlow = vi.fn().mockResolvedValue({})

        setup(loadFlow)
        await flushPromises()

        expect(loadFlow).toHaveBeenCalledWith({namespace: "ns", id: "flow", optional: true})
    })
})

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

        store.setScopedIterations({per_region: iteration("it-0", "child-0")} as never)
        await flushPromises()
        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child-0"])

        store.setScopedIterations({per_region: iteration("it-1", "child-1")} as never)
        await flushPromises()

        expect(subscriptions.map((subscription) => subscription.executionId)).toEqual(["child-0", "child-1"])
        expect(subscriptions[0].close).toHaveBeenCalled()
    })
})
