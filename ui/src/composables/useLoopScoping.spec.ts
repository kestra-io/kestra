import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
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
const {useLoopScoping, loopLaneNodes, SCOPED_ITERATIONS_REFRESH_MS} = await import("./useLoopScoping")
const {AUTO_REFRESH_MS} = await import("./useLoopMergedLogs")
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

const iterationExecution = (id: string, state = "RUNNING", taskRunId = "tr-loop") => ({
    id,
    state: {current: state},
    taskRunList: [{id: "tr-quarter", taskId: "per_quarter", state: {current: "SUCCESS"}}],
    loopRun: {value: "AMER", index: Number(id.replace("it-", "")) - 1, taskRunId},
})

const nestedGraph = {
    nodes: [],
    edges: [],
    clusters: [
        ...graph.clusters,
        {cluster: {uid: "cluster_per_region.per_quarter", type: "x", taskNode: {uid: "per_region.per_quarter", task: {type: LOOP_TYPE}}}, nodes: [], parents: [], start: "", end: ""},
    ],
}

const taskRun = (id: string, state = "SUCCESS") => ({id, taskId: "per_region", state: {current: state}})

const executionWithRuns = (...taskRuns: ReturnType<typeof taskRun>[]) => ({...rootExecution, taskRunList: taskRuns})

function setup(enabled = true, flowGraph: typeof graph = graph, execution: unknown = rootExecution) {
    const store = useExecutionsStore()
    store.execution = execution as never
    store.subscribeToExecution = vi.fn() as never

    let scoping!: ReturnType<typeof useLoopScoping>
    const enabledRef = ref(enabled)
    const wrapper = mount(defineComponent({
        setup() {
            scoping = useLoopScoping(computed(() => flowGraph as never), computed(() => enabledRef.value))
            return () => null
        },
    }))
    return {store, scoping, wrapper, route: useRoute() as unknown as {query: Record<string, string>}}
}

describe("useLoopScoping", () => {
    afterEach(() => {
        vi.useRealTimers()
    })

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
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        expect(mocks.findIterationByNumber).toHaveBeenCalledWith("root", "per_region", 2, "tr-loop")
        expect(store.scopedIterations.per_region.id).toBe("it-2")
        expect(store.subflowsExecutions).toEqual({})
    })

    it("shouldStillResolveAOneBasedLinkAndRewriteItAsAnIndex", async () => {
        const {store, scoping, route} = setup()
        route.query = {loopScope: "per_region:2"}
        await flushPromises()

        expect(mocks.findIterationByNumber).toHaveBeenCalledWith("root", "per_region", 2, "tr-loop")
        expect(store.scopedIterations.per_region.id).toBe("it-2")
        scoping.stepLane("per_region", 1)
        await flushPromises()
        expect(route.query.loopScope).toBe("per_region@2")
    })

    it("shouldDropAScopeThatNoLongerExists", async () => {
        mocks.findIterationByNumber.mockResolvedValue(undefined)
        const {route} = setup()
        route.query = {loopScope: "per_region@98"}
        await flushPromises()

        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldKeepTheScopeAndReportAFailureOnATransientError", async () => {
        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("unknown"))
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        expect(route.query.loopScope).toBe("per_region@1")
        expect(scoping.scopeFailure.value).toBe("unknown")
    })

    it("shouldTreatAForbiddenIterationAsDefinitive", async () => {
        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("forbidden"))
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        expect(scoping.scopeFailure.value).toBe("forbidden")
        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldNotReportANumberWhoseIterationIsNotTheLoadedOne", async () => {
        const {scoping, route, store} = setup()
        route.query = {loopScope: "per_region@1"}
        await flushPromises()
        expect(scoping.lanes.value.per_region.scopedNumber).toBe(2)

        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("unknown"))
        route.query = {loopScope: "per_region@2"}
        await flushPromises()

        expect(store.scopedIterations.per_region.id).toBe("it-2")
        expect(scoping.lanes.value.per_region.scopedNumber).toBeUndefined()
    })

    it("shouldStepFromTheFirstStartedIterationAndClampAtTheBounds", async () => {
        const {scoping, route} = setup()
        await flushPromises()

        scoping.stepLane("per_region", 1)
        await flushPromises()
        expect(route.query.loopScope).toBe("per_region@0")

        scoping.stepLane("per_region", -1)
        await flushPromises()
        expect(route.query.loopScope).toBe("per_region@0")
    })

    it("shouldStepFromTheRequestedIterationWhileItIsStillLoading", async () => {
        const {scoping, route} = setup()
        route.query = {loopScope: "per_region@0"}
        await flushPromises()
        mocks.loadIterationExecution.mockReturnValue(new Promise(() => {}))

        scoping.stepLane("per_region", 1)
        await flushPromises()
        scoping.stepLane("per_region", 1)
        await flushPromises()

        expect(route.query.loopScope).toBe("per_region@2")
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

        expect(route.query.loopScope).toBe("per_region@2")
    })

    it("shouldMoveTheOuterScopeWhenTheScopedOuterIterationHasNoInnerFailure", async () => {
        mocks.findFirstFailedIteration.mockResolvedValue(undefined)
        mocks.findFailedIterationChain.mockResolvedValue({entries: [{taskId: "per_region", number: 2}, {taskId: "per_quarter", number: 3}], leafId: "q-3"})
        const {scoping, route} = setup(true, nestedGraph)
        route.query = {loopScope: "per_region@0"}
        await flushPromises()

        await scoping.scopeFirstFailure("per_region.per_quarter")
        await flushPromises()

        expect(mocks.findFirstFailedIteration).toHaveBeenCalledWith("it-1", "per_quarter", "tr-quarter")
        expect(route.query.loopScope).toBe("per_region@1,per_quarter@2")
    })

    it("shouldStayInTheScopedOuterIterationWhenItHasTheFailure", async () => {
        mocks.findFirstFailedIteration.mockImplementation(async (parentId: string) => parentId === "it-2" ? {id: "q-1", number: 1, state: "FAILED"} : undefined)
        const {scoping, route} = setup(true, nestedGraph)
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        await scoping.scopeFirstFailure("per_region.per_quarter")
        await flushPromises()

        expect(mocks.findFailedIterationChain).not.toHaveBeenCalled()
        expect(route.query.loopScope).toBe("per_region@1,per_quarter@0")
    })

    it("shouldLeaveASubflowExpansionUntouchedWhenTheScopeIsCleared", async () => {
        const {scoping, store, route} = setup()
        store.addSubflowExecution({subflow: "per_region", execution: {id: "subflow-1"} as never})
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        scoping.clearScope()
        await flushPromises()

        expect(store.scopedIterations).toEqual({})
        expect(store.subflowsExecutions.per_region.id).toBe("subflow-1")
    })

    it("shouldDropScopedIterationsWhenTheComponentUnmounts", async () => {
        const {store, route, wrapper} = setup()
        route.query = {loopScope: "per_region@1"}
        await flushPromises()

        wrapper.unmount()

        expect(store.scopedIterations).toEqual({})
    })

    it("shouldNotWriteTheScopeOnceUnmountedWhileTheIterationIsLoading", async () => {
        let resolveIteration!: (value: unknown) => void
        mocks.loadIterationExecution.mockReturnValue(new Promise((resolve) => (resolveIteration = resolve)))
        const {store, route, wrapper} = setup()
        route.query = {loopScope: "per_region@1"}
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

    it("shouldLeaveTheScopeOfAnotherViewWhenADisabledInstanceUnmounts", async () => {
        const {store, wrapper} = setup(false)
        store.setScopedIterations({per_region: iterationExecution("it-2", "SUCCESS")} as never)
        await flushPromises()

        wrapper.unmount()

        expect(store.scopedIterations.per_region.id).toBe("it-2")
    })

    it("shouldDoNothingOutsideAReadOnlyExecution", async () => {
        const {scoping} = setup(false)
        await flushPromises()

        expect(scoping.laneNodes.value).toEqual([])
        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
    })

    it("shouldNotShowTheCountsOfALoopThatRanSeveralTimesInTheSameExecution", async () => {
        const {scoping} = setup(true, graph, executionWithRuns(taskRun("tr-loop-1"), taskRun("tr-loop-2")))
        await flushPromises()

        expect(scoping.lanes.value.per_region.status).toBe("unknown")
        expect(scoping.hostExecutionId("per_region")).toBeUndefined()
        expect(mocks.loadTaskRunOutputs).not.toHaveBeenCalled()
    })

    it("shouldReportAnAmbiguousScopeWhenSeveralRunsOfTheLoopShareTheIteration", async () => {
        mocks.findIterationByNumber.mockRejectedValue(new LoopIterationError("ambiguous"))
        const {scoping, route} = setup(true, graph, executionWithRuns(taskRun("tr-loop-1"), taskRun("tr-loop-2")))
        route.query = {loopScope: "per_region@0"}
        await flushPromises()

        expect(mocks.findIterationByNumber).toHaveBeenCalledWith("root", "per_region", 1, undefined)
        expect(scoping.scopeFailure.value).toBe("ambiguous")
        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldRefuseToJumpToAFailureOfALoopThatRanSeveralTimes", async () => {
        const {scoping, route} = setup(true, graph, executionWithRuns(taskRun("tr-loop-1"), taskRun("tr-loop-2")))
        await flushPromises()

        await scoping.scopeFirstFailure("per_region")

        expect(scoping.scopeFailure.value).toBe("ambiguous")
        expect(mocks.findFirstFailedIteration).not.toHaveBeenCalled()
        expect(mocks.findFailedIterationChain).not.toHaveBeenCalled()
        expect(route.query.loopScope).toBeUndefined()
    })

    it("shouldReportThatTheFailureCouldNotBeLocated", async () => {
        mocks.findFirstFailedIteration.mockResolvedValue(undefined)
        mocks.findFailedIterationChain.mockResolvedValue(undefined)
        const {scoping, route} = setup()
        await flushPromises()

        await scoping.scopeFirstFailure("per_region")

        expect(scoping.scopeFailure.value).toBe("not-located")
        expect(route.query.loopScope).toBeUndefined()

        scoping.scopeLane("per_region", 2)
        expect(scoping.scopeFailure.value).toBeUndefined()
    })

    it("shouldStopDescendingWhenTheFailedIterationRanTheNextLoopSeveralTimes", async () => {
        mocks.findFirstFailedIteration.mockResolvedValue({id: "it-5", number: 5, state: "FAILED"})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => ({
            ...iterationExecution(id, "FAILED"),
            taskRunList: [
                {id: "tr-quarter-1", taskId: "per_quarter", state: {current: "FAILED"}},
                {id: "tr-quarter-2", taskId: "per_quarter", state: {current: "SUCCESS"}},
            ],
        }))
        const {scoping, route} = setup(true, nestedGraph)
        await flushPromises()

        await scoping.scopeFirstFailure("per_region")
        await flushPromises()

        expect(mocks.findFirstFailedIteration).toHaveBeenCalledTimes(1)
        expect(route.query.loopScope).toBe("per_region@4")
    })

    it("shouldNotOpenAnyStreamForARunningNestedScope", async () => {
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {store, route} = setup(true, nestedGraph)
        route.query = {loopScope: "per_region@0,per_quarter@1"}
        await flushPromises()

        expect(Object.keys(store.scopedIterations)).toEqual(["per_region", "per_region.per_quarter"])
        expect(store.subscribeToExecution).not.toHaveBeenCalled()
    })

    it("shouldRefetchARunningScopedIterationWhileTheRootExecutionDoesNotChange", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id, id === "it-1" ? "RUNNING" : "SUCCESS"))
        const {store, route} = setup(true, nestedGraph)
        route.query = {loopScope: "per_region@0,per_quarter@1"}
        await flushPromises()
        mocks.loadIterationExecution.mockClear()
        mocks.loadIterationExecution.mockImplementation(async (id: string) => ({
            ...iterationExecution(id, "RUNNING"),
            taskRunList: [{id: "tr-quarter", taskId: "per_quarter", state: {current: "RUNNING"}}, {id: "progress", taskId: "x", state: {current: "RUNNING"}}],
        }))
        const rootBefore = store.execution

        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS - 1)
        expect(mocks.loadIterationExecution).not.toHaveBeenCalled()
        await vi.advanceTimersByTimeAsync(1)

        expect(store.execution).toBe(rootBefore)
        expect(mocks.loadIterationExecution.mock.calls).toEqual([["it-1"]])
        expect(store.scopedIterations.per_region.taskRunList).toHaveLength(2)
    })

    it("shouldStopRefetchingOnceTheScopedIterationIsTerminal", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {route} = setup()
        route.query = {loopScope: "per_region@0"}
        await flushPromises()
        mocks.loadIterationExecution.mockClear()
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id, "SUCCESS"))

        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS)
        expect(mocks.loadIterationExecution).toHaveBeenCalledTimes(1)
        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS * 5)

        expect(mocks.loadIterationExecution).toHaveBeenCalledTimes(1)
    })

    it("shouldStopRefetchingWhenTheComponentUnmounts", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {route, wrapper} = setup()
        route.query = {loopScope: "per_region@0"}
        await flushPromises()
        mocks.loadIterationExecution.mockClear()

        wrapper.unmount()
        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS * 5)

        expect(mocks.loadIterationExecution).not.toHaveBeenCalled()
    })

    it("shouldStopRefetchingWhenTheScopeIsCleared", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {route, scoping} = setup()
        route.query = {loopScope: "per_region@0"}
        await flushPromises()
        mocks.loadIterationExecution.mockClear()

        scoping.clearScope()
        await flushPromises()
        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS * 5)

        expect(mocks.loadIterationExecution).not.toHaveBeenCalled()
    })

    it("shouldDropARefetchedIterationThatIsNoLongerScoped", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        mocks.loadIterationExecution.mockImplementation(async (id: string) => iterationExecution(id))
        const {store, route} = setup()
        route.query = {loopScope: "per_region@0"}
        await flushPromises()
        let release!: (value: unknown) => void
        mocks.loadIterationExecution.mockReturnValueOnce(new Promise((resolve) => (release = resolve)))

        await vi.advanceTimersByTimeAsync(SCOPED_ITERATIONS_REFRESH_MS)
        store.setScopedIterations({per_region: iterationExecution("it-2")} as never)
        release({...iterationExecution("it-1"), taskRunList: [{id: "stale", taskId: "x", state: {current: "RUNNING"}}]})
        await vi.advanceTimersByTimeAsync(0)

        expect(store.scopedIterations.per_region.id).toBe("it-2")
        expect(store.scopedIterations.per_region.taskRunList?.map((run) => run.id)).not.toContain("stale")
    })

    it("shouldNotRefetchTheOutputsOfARunningLoopMoreThanOncePerWindow", async () => {
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout", "Date"]})
        const running = executionWithRuns(taskRun("tr-loop", "RUNNING"))
        const {store} = setup(true, graph, running)
        await vi.advanceTimersByTimeAsync(0)
        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        for (let tick = 0; tick < 5; tick++) {
            store.execution = {...running} as never
            await vi.advanceTimersByTimeAsync(1_100)
        }
        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS)
        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledTimes(2)

        store.execution = executionWithRuns(taskRun("tr-loop", "SUCCESS")) as never
        await vi.advanceTimersByTimeAsync(1_100)
        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledTimes(3)

        await vi.advanceTimersByTimeAsync(AUTO_REFRESH_MS * 3)
        expect(mocks.loadTaskRunOutputs).toHaveBeenCalledTimes(3)
    })
})
