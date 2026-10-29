import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"

const flowStore = reactive({
    flow: {id: "f", namespace: "ns", revision: 2, inputs: []} as Record<string, unknown>,
    haveChange: false,
    flowErrors: undefined,
    isCreating: false,
    saveAsDraft: vi.fn(() => Promise.resolve()),
})

const executionsStore = reactive({
    execution: undefined as Record<string, unknown> | undefined,
    flow: undefined,
    triggerExecution: vi.fn(),
    replayExecution: vi.fn(),
    loadLogs: vi.fn(),
    setReusedTaskRuns: vi.fn(),
    clearReusedTaskRuns: vi.fn(),
})

const playgroundApi = vi.hoisted(() => ({
    planPlaygroundRun: vi.fn(),
    listPlaygroundRuns: vi.fn(),
    listReusedTaskRuns: vi.fn(),
    reportPlaygroundClient: vi.fn(),
    releasePlaygroundClient: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/playground", () => playgroundApi)
vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))
vi.mock("./flow", () => ({useFlowStore: () => flowStore}))
vi.mock("./executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("./fileExplorer", () => ({useFileExplorerStore: () => ({loadNodes: vi.fn()})}))

const replayPlan = {buildOnExecutionId: "e1", replayFromTaskRunId: "tr-b1", replayFromTaskId: "b", reusedTaskIds: ["a"], breakpoints: ["c"], freshReason: "NONE"}
const replayed = {id: "e2", kind: "PLAYGROUND", flowRevision: 2, state: {current: "SUCCESS"}}

async function store() {
    const {usePlaygroundStore} = await import("./playground")
    return usePlaygroundStore()
}

describe("playground runs planned by the server", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        executionsStore.execution = undefined
        playgroundApi.listReusedTaskRuns.mockResolvedValue({taskRuns: {}})
        executionsStore.replayExecution.mockResolvedValue(replayed)
        executionsStore.triggerExecution.mockResolvedValue({...replayed, id: "fresh"})
    })

    it("replays the run the server chose to build on, from the task it chose, with its breakpoints", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue(replayPlan)
        const playground = await store()

        await playground.runUntilTask("b")

        expect(playgroundApi.planPlaygroundRun).toHaveBeenCalledWith(expect.objectContaining({namespace: "ns", id: "f", revision: 2, taskId: "b", mode: "UP_TO", buildOn: "LATEST"}))
        expect(executionsStore.replayExecution).toHaveBeenCalledWith({executionId: "e1", taskRunId: "tr-b1", revision: 2, breakpoints: ["c"]})
        expect(executionsStore.triggerExecution).not.toHaveBeenCalled()
        expect(playground.latestExecution?.id).toBe("e2")
    })

    it("runs from the task to the end without breakpoints when downstream tasks are asked for", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue({...replayPlan, breakpoints: []})
        const playground = await store()

        await playground.runUntilTask("b", true)

        expect(playgroundApi.planPlaygroundRun).toHaveBeenCalledWith(expect.objectContaining({mode: "FROM"}))
    })

    it("starts a fresh run that stops where the server says when nothing can be reused", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue({reusedTaskIds: [], breakpoints: ["c"], freshReason: "VARIABLES_CHANGED"})
        const playground = await store()

        await playground.runUntilTask("b")

        expect(executionsStore.replayExecution).not.toHaveBeenCalled()
        expect(executionsStore.triggerExecution).toHaveBeenCalledWith(expect.objectContaining({kind: "PLAYGROUND", breakpoints: ["c"], revision: 2}))
    })

    it("starts a fresh run when the run to build on was purged after the plan", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue(replayPlan)
        executionsStore.replayExecution.mockRejectedValueOnce({response: {status: 404}})
        const playground = await store()

        await playground.runUntilTask("b")

        expect(executionsStore.triggerExecution).toHaveBeenCalledOnce()
        expect(playground.latestExecution?.id).toBe("fresh")
    })

    it("marks the reused task runs and carries over the logs of the run that produced them", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue(replayPlan)
        playgroundApi.listReusedTaskRuns.mockResolvedValue({taskRuns: {"tr-a2": {executionId: "e0", taskRunId: "tr-a0"}}})
        executionsStore.loadLogs.mockResolvedValue([
            {executionId: "e0", taskRunId: "tr-a0", message: "a ran"},
            {executionId: "e0", taskRunId: "tr-other", message: "not reused"},
        ])
        const playground = await store()

        await playground.runUntilTask("b")

        expect(executionsStore.loadLogs).toHaveBeenCalledWith(expect.objectContaining({executionId: "e0", store: false}))
        expect(executionsStore.setReusedTaskRuns).toHaveBeenCalledWith("e2", ["tr-a2"], [{executionId: "e2", taskRunId: "tr-a2", message: "a ran"}])
    })

    it("builds the next run on nothing after the history is cleared, then on the latest run again", async () => {
        playgroundApi.planPlaygroundRun.mockResolvedValue({reusedTaskIds: [], breakpoints: [], freshReason: "BUILD_ON_NOTHING"})
        const playground = await store()

        playground.clearExecutions()
        await playground.runUntilTask("b")

        expect(playgroundApi.planPlaygroundRun).toHaveBeenCalledWith(expect.objectContaining({buildOn: "NOTHING"}))
        expect(playground.buildOn).toBe("LATEST")
    })

    it("restores the runs kept on the server, newest first", async () => {
        playgroundApi.listPlaygroundRuns.mockResolvedValue({results: [replayed, {id: "e1", state: {current: "KILLED"}}], total: 2})
        const playground = await store()

        await playground.loadSession()

        expect(playground.executions.map(execution => execution.id)).toEqual(["e2", "e1"])
        expect(playgroundApi.listReusedTaskRuns).toHaveBeenCalledWith(expect.objectContaining({executionId: "e2"}))
    })
})
