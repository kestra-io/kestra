import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {computed, defineComponent, ref} from "vue"
import {flushPromises, mount} from "@vue/test-utils"

vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    const route = reactive<{query: Record<string, string>}>({query: {}})
    return {
        useRoute: () => route,
        useRouter: () => ({
            replace: vi.fn(({query}: {query: Record<string, string>}) => {
                route.query = query
            }),
            push: vi.fn(),
            beforeEach: vi.fn(),
            afterEach: vi.fn(),
        }),
    }
})

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

const mocks = vi.hoisted(() => ({
    findIterationByNumber: vi.fn(),
    loadIterationExecution: vi.fn(),
    findFirstFailedIteration: vi.fn(),
    findFailedIterationChain: vi.fn(),
    loadTaskRunOutputs: vi.fn(),
}))

vi.mock("../utils/loopIterations", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../utils/loopIterations")>()),
    findIterationByNumber: mocks.findIterationByNumber,
    loadIterationExecution: mocks.loadIterationExecution,
    findFirstFailedIteration: mocks.findFirstFailedIteration,
    findFailedIterationChain: mocks.findFailedIterationChain,
}))

vi.mock("./useTaskRunOutputs", () => ({loadTaskRunOutputs: mocks.loadTaskRunOutputs}))

const {useRoute} = await import("vue-router")
const {useExecutionsStore} = await import("../stores/executions")
const {useLoopScoping, loopLaneNodes} = await import("./useLoopScoping")
const {LoopIterationError} = await import("../utils/loopIterations")

const LOOP_TYPE = "io.kestra.plugin.core.flow.Loop"
const graph = {
    nodes: [],
    edges: [],
    clusters: [{cluster: {uid: "cluster_per_region", type: "x", taskNode: {uid: "per_region", task: {type: LOOP_TYPE}}}, nodes: [], parents: [], start: "", end: ""}],
}

const rootExecution = {
    id: "root",
    namespace: "ns",
    flowId: "flow",
    state: {current: "SUCCESS", startDate: "2026-10-09T09:49:13.994699Z"},
    taskRunList: [{id: "tr-loop", taskId: "per_region", state: {current: "SUCCESS"}}],
}

const iterationExecution = (id: string, state = "RUNNING") => ({id, state: {current: state}, taskRunList: [], loopRun: {value: "AMER", index: Number(id.replace("it-", "")) - 1}})

function setup(enabled = true) {
    const store = useExecutionsStore()
    store.execution = rootExecution as never
    const subscriptions: {executionId: string; close: ReturnType<typeof vi.fn>; emit: (e: unknown) => void}[] = []
    store.subscribeToExecution = vi.fn((executionId: string, handlers: {onExecution: (e: never) => void}) => {
        const subscription = {executionId, close: vi.fn(), emit: (e: unknown) => handlers.onExecution(e as never)}
        subscriptions.push(subscription)
        return subscription
    }) as never

    let scoping!: ReturnType<typeof useLoopScoping>
    const enabledRef = ref(enabled)
    const wrapper = mount(defineComponent({
        setup() {
            scoping = useLoopScoping(computed(() => graph as never), computed(() => enabledRef.value))
            return () => null
        },
    }))
    return {store, scoping, subscriptions, wrapper, route: useRoute() as unknown as {query: Record<string, string>}}
}

describe("useLoopScoping", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        Object.values(mocks).forEach((mock) => mock.mockReset())
        ;(useRoute() as unknown as {query: Record<string, string>}).query = {}
        mocks.loadTaskRunOutputs.mockResolvedValue({iterationCount: 4, runningIterations: 0, terminatedIterations: {SUCCESS: 4}})
        mocks.findIterationByNumber.mockImplementation(async (_parent: string, _task: string, number: number) => ({id: `it-${number}`, number, state: "SUCCESS"}))
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id, "SUCCESS"))
    })

    it("shouldRegisterTheScopedIterationExecutionFromTheUrl", async () => {
        mocks.findIterationByNumber.mockResolvedValue({id: "it-2", number: 2, state: "SUCCESS"})
        mocks.loadIterationExecution.mockResolvedValue(iterationExecution("it-2", "SUCCESS"))
        const {store, route} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        expect(mocks.findIterationByNumber).toHaveBeenCalledWith("root", "per_region", 2)
        expect(store.scopedIterations.per_region.id).toBe("it-2")
        expect(store.subflowsExecutions).toEqual({})
    })

    it("shouldDropAScopeThatNoLongerExists", async () => {
        mocks.findIterationByNumber.mockResolvedValue(undefined)
        const {route} = setup()
        route.query = {loopScope: "per_region:99"}
        await flushPromises()

        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldKeepTheScopeAndReportAFailureOnATransientError", async () => {
        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("unknown"))
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        expect(route.query.loopScope).toBe("per_region:2")
        expect(scoping.scopeFailure.value).toBe("unknown")
    })

    it("shouldTreatAForbiddenIterationAsDefinitive", async () => {
        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("forbidden"))
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        expect(scoping.scopeFailure.value).toBe("forbidden")
        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldNotReportANumberWhoseIterationIsNotTheLoadedOne", async () => {
        const {scoping, route, store} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()
        expect(scoping.lanes.value.per_region.scopedNumber).toBe(2)

        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("unknown"))
        route.query = {loopScope: "per_region:3"}
        await flushPromises()

        expect(store.scopedIterations.per_region.id).toBe("it-2")
        expect(scoping.lanes.value.per_region.scopedNumber).toBeUndefined()
    })

    it("shouldReplaceTheStreamWhenSteppingToAnotherRunningIteration", async () => {
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {store, subscriptions, route} = setup()
        route.query = {loopScope: "per_region:1"}
        await flushPromises()
        expect(subscriptions.map((s) => s.executionId)).toEqual(["it-1"])

        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        expect(subscriptions.map((s) => s.executionId)).toEqual(["it-1", "it-2"])
        expect(subscriptions[0].close).toHaveBeenCalled()
        subscriptions[0].emit({id: "it-1", state: {current: "RUNNING"}, taskRunList: [{id: "stale"}]})
        expect(store.scopedIterations.per_region.id).toBe("it-2")
    })

    it("shouldStepFromTheFirstStartedIterationAndClampAtTheBounds", async () => {
        const {scoping, route} = setup()
        await flushPromises()

        scoping.stepLane("per_region", 1)
        await flushPromises()
        expect(route.query.loopScope).toBe("per_region:1")

        scoping.stepLane("per_region", -1)
        await flushPromises()
        expect(route.query.loopScope).toBe("per_region:1")
    })

    it("shouldStepFromTheRequestedIterationWhileItIsStillLoading", async () => {
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region:1"}
        await flushPromises()
        mocks.loadIterationExecution.mockReturnValue(new Promise(() => {}))

        scoping.stepLane("per_region", 1)
        await flushPromises()
        scoping.stepLane("per_region", 1)
        await flushPromises()

        expect(route.query.loopScope).toBe("per_region:3")
    })

    it("shouldNotOverwriteANewerScopeWhenAFailureLookupResolvesLate", async () => {
        let resolveFailure!: (value: unknown) => void
        mocks.findFirstFailedIteration.mockReturnValue(new Promise((resolve) => (resolveFailure = resolve)))
        const {scoping, route} = setup()
        await flushPromises()

        const pending = scoping.scopeFirstFailure("per_region")
        scoping.scopeLane("per_region", 3)
        await flushPromises()
        resolveFailure({id: "failed", number: 1, state: "FAILED"})
        await pending
        await flushPromises()

        expect(route.query.loopScope).toBe("per_region:3")
    })

    it("shouldLeaveASubflowExpansionUntouchedWhenTheScopeIsCleared", async () => {
        const {scoping, store, route} = setup()
        store.addSubflowExecution({subflow: "per_region", execution: {id: "subflow-1"} as never})
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        scoping.clearScope()
        await flushPromises()

        expect(store.scopedIterations).toEqual({})
        expect(store.subflowsExecutions.per_region.id).toBe("subflow-1")
    })

    it("shouldDropScopedIterationsWhenTheComponentUnmounts", async () => {
        const {store, route, wrapper} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        wrapper.unmount()

        expect(store.scopedIterations).toEqual({})
    })

    it.each(["SUBMITTED", "QUEUED", "RETRYING", "BREAKPOINT"])("shouldFollowAnIterationThatIsStill%s", async (state) => {
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id, state))
        const {subscriptions, route} = setup()
        route.query = {loopScope: "per_region:1"}
        await flushPromises()

        expect(subscriptions.map((s) => s.executionId)).toEqual(["it-1"])
    })

    it.each(["SUCCESS", "FAILED", "KILLED", "WARNING", "CANCELLED", "SKIPPED"])("shouldNotFollowAnIterationThatIs%s", async (state) => {
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id, state))
        const {subscriptions, route} = setup()
        route.query = {loopScope: "per_region:1"}
        await flushPromises()

        expect(subscriptions).toEqual([])
    })

    it("shouldNotWriteTheScopeOnceUnmountedWhileTheIterationIsLoading", async () => {
        let resolveIteration!: (value: unknown) => void
        mocks.loadIterationExecution.mockReturnValue(new Promise((resolve) => (resolveIteration = resolve)))
        const {store, route, wrapper} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        wrapper.unmount()
        resolveIteration(iterationExecution("it-2", "SUCCESS"))
        await flushPromises()

        expect(store.scopedIterations).toEqual({})
    })

    it("shouldStopLoadingALaneWhoseOutputsCannotBeFetched", async () => {
        mocks.loadTaskRunOutputs.mockRejectedValue(new Error("502"))
        const {scoping} = setup()
        await flushPromises()

        expect(scoping.lanes.value.per_region.status).toBe("unknown")
    })

    it("shouldIgnoreLoopsInsideAnExpandedSubflow", () => {
        const lanes = loopLaneNodes({
            clusters: [
                {cluster: {uid: "cluster_call_child", type: "io.kestra.core.models.hierarchies.SubflowGraphCluster", taskNode: {uid: "call_child", task: {type: "io.kestra.plugin.core.flow.Subflow"}}}},
                {cluster: {uid: "cluster_call_child.inner_loop", type: "x", taskNode: {uid: "call_child.inner_loop", task: {type: LOOP_TYPE}}}},
                {cluster: {uid: "cluster_per_region", type: "x", taskNode: {uid: "per_region", task: {type: LOOP_TYPE}}}},
            ],
        } as never)

        expect(lanes.map((lane) => lane.uid)).toEqual(["per_region"])
    })

    it("shouldDoNothingOutsideAReadOnlyExecution", async () => {
        const {scoping} = setup(false)
        await flushPromises()

        expect(scoping.laneNodes.value).toEqual([])
        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
    })
})
