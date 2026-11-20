import {beforeEach, afterEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {nextTick, reactive} from "vue"

const graph = {nodes: [{task: {id: "b"}}], edges: []}
const previousFlow = {inputs: [], labels: [], variables: {greeting: "hello"}}
const flowAt = (revision: number, greeting: string) => ({id: "f", namespace: "ns", revision, inputs: [], labels: [], variables: {greeting}})

const flowStore = reactive({
    flow: {} as Record<string, unknown>,
    haveChange: false,
    flowErrors: undefined,
    isCreating: false,
    saveAsDraft: vi.fn(() => Promise.resolve()),
    loadGraph: vi.fn(() => Promise.resolve(graph)),
    loadFlow: vi.fn(() => Promise.resolve(previousFlow)),
})

const reused: Record<string, {taskRunIds: string[]; logs: unknown[]}> = {}

const executionsStore = reactive({
    execution: undefined as Record<string, unknown> | undefined,
    flow: undefined,
    triggerExecution: vi.fn(),
    replayExecution: vi.fn(),
    loadLogs: vi.fn(),
    reusedLogsOf: vi.fn((executionId: string) => reused[executionId]?.logs ?? []),
    setReusedTaskRuns: vi.fn((executionId: string, taskRunIds: string[], logs: unknown[]) => {
        reused[executionId] = {taskRunIds, logs}
    }),
    clearReusedTaskRuns: vi.fn(),
})

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))
vi.mock("./flow", () => ({useFlowStore: () => flowStore}))
vi.mock("./executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("./fileExplorer", () => ({useFileExplorerStore: () => ({loadNodes: vi.fn()})}))
vi.mock("../utils/toast", () => ({useToast: () => ({confirm: vi.fn(), error: vi.fn()})}))
vi.mock("@kestra-io/topology/vue-flow-utils", () => ({
    areTasksIdenticalInGraphUntilTask: () => true,
    getNextTaskNodes: () => [],
}))

const runningExecution = {id: "e1", kind: "PLAYGROUND", flowRevision: 1, state: {current: "CREATED"}}

async function playgroundAfterFirstRun(taskRunList: Record<string, unknown>[] = [{id: "tr-b", taskId: "b"}]) {
    const {usePlaygroundStore} = await import("./playground")
    const playground = usePlaygroundStore()

    executionsStore.triggerExecution.mockResolvedValueOnce(runningExecution)
    flowStore.flow = flowAt(1, "hello")
    await playground.runUntilTask("b")

    executionsStore.execution = {...runningExecution, state: {current: "SUCCESS"}, taskRunList}
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)

    return playground
}

describe("playground reuse of earlier task results", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
        vi.clearAllMocks()
        executionsStore.execution = undefined
        Object.keys(reused).forEach(executionId => delete reused[executionId])
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("replays from the reused task run when the flow variables are unchanged", async () => {
        const playground = await playgroundAfterFirstRun()

        flowStore.flow = flowAt(2, "hello")
        await playground.runUntilTask("b")

        expect(executionsStore.replayExecution).toHaveBeenCalledTimes(1)
        expect(executionsStore.triggerExecution).toHaveBeenCalledTimes(1)
    })

    it("starts a fresh execution when the flow variables changed since the last run", async () => {
        const playground = await playgroundAfterFirstRun()

        flowStore.flow = flowAt(2, "hi")
        await playground.runUntilTask("b")

        expect(executionsStore.replayExecution).not.toHaveBeenCalled()
        expect(executionsStore.triggerExecution).toHaveBeenCalledTimes(2)
    })

    it("starts a fresh execution when the run to replay was purged", async () => {
        const playground = await playgroundAfterFirstRun()
        executionsStore.replayExecution.mockRejectedValueOnce({response: {status: 404}})

        flowStore.flow = flowAt(2, "hello")
        await playground.runUntilTask("b")

        expect(executionsStore.replayExecution).toHaveBeenCalledTimes(1)
        expect(executionsStore.triggerExecution).toHaveBeenCalledTimes(2)
    })

    it("marks the task runs a replay reuses and keeps their logs, with their original timestamps", async () => {
        const playground = await playgroundAfterFirstRun([
            {id: "tr-a", taskId: "a", state: {current: "SUCCESS"}},
            {id: "tr-b", taskId: "b", state: {current: "SUCCESS"}},
        ])
        executionsStore.replayExecution.mockResolvedValueOnce({
            id: "e2",
            state: {current: "RESTARTED"},
            taskRunList: [
                {id: "tr-a2", taskId: "a", state: {current: "SUCCESS"}},
                {id: "tr-b2", taskId: "b", state: {current: "RESTARTED"}},
            ],
        })
        executionsStore.loadLogs.mockResolvedValueOnce([
            {executionId: "e1", taskRunId: "tr-a", timestamp: "2026-01-01T00:00:00Z", message: "a ran"},
            {executionId: "e1", taskRunId: "tr-b", timestamp: "2026-01-01T00:00:01Z", message: "b ran"},
            {executionId: "e1", timestamp: "2026-01-01T00:00:02Z", message: "execution log"},
        ])

        await playground.runUntilTask("b")

        expect(executionsStore.loadLogs).toHaveBeenCalledWith({executionId: "e1", filters: [{field: "kind", operation: "IN", value: "PLAYGROUND"}], store: false})
        expect(reused.e2).toEqual({
            taskRunIds: ["tr-a2"],
            logs: [{executionId: "e2", taskRunId: "tr-a2", timestamp: "2026-01-01T00:00:00Z", message: "a ran"}],
        })
    })

    it("keeps the logs a replay carried over when that replay is replayed in turn", async () => {
        const playground = await playgroundAfterFirstRun([
            {id: "tr-a", taskId: "a", state: {current: "SUCCESS"}},
            {id: "tr-b", taskId: "b", state: {current: "SUCCESS"}},
            {id: "tr-c", taskId: "c", state: {current: "SUCCESS"}},
        ])
        const replayed = {
            id: "e2",
            kind: "PLAYGROUND",
            flowRevision: 1,
            state: {current: "RESTARTED"},
            taskRunList: [
                {id: "tr-a2", taskId: "a", state: {current: "SUCCESS"}},
                {id: "tr-b2", taskId: "b", state: {current: "RESTARTED"}},
            ],
        }
        executionsStore.replayExecution.mockResolvedValueOnce(replayed)
        executionsStore.loadLogs.mockResolvedValueOnce([{executionId: "e1", taskRunId: "tr-a", timestamp: "t-a", message: "a ran"}])
        await playground.runUntilTask("b")
        executionsStore.execution = {...replayed, state: {current: "SUCCESS"}, taskRunList: [
            {id: "tr-a2", taskId: "a", state: {current: "SUCCESS"}},
            {id: "tr-b2", taskId: "b", state: {current: "SUCCESS"}},
            {id: "tr-c2", taskId: "c", state: {current: "SUCCESS"}},
        ]}
        await nextTick()
        await vi.advanceTimersByTimeAsync(1000)

        executionsStore.replayExecution.mockResolvedValueOnce({
            id: "e3",
            state: {current: "RESTARTED"},
            taskRunList: [
                {id: "tr-a3", taskId: "a", state: {current: "SUCCESS"}},
                {id: "tr-b3", taskId: "b", state: {current: "SUCCESS"}},
                {id: "tr-c3", taskId: "c", state: {current: "RESTARTED"}},
            ],
        })
        executionsStore.loadLogs.mockResolvedValueOnce([{executionId: "e2", taskRunId: "tr-b2", timestamp: "t-b", message: "b ran"}])
        await playground.runUntilTask("c")

        expect(reused.e3).toEqual({
            taskRunIds: ["tr-a3", "tr-b3"],
            logs: [
                {executionId: "e3", taskRunId: "tr-a3", timestamp: "t-a", message: "a ran"},
                {executionId: "e3", taskRunId: "tr-b3", timestamp: "t-b", message: "b ran"},
            ],
        })
    })

    it("still marks the reused task runs when the logs of the run it builds on fail to load", async () => {
        const playground = await playgroundAfterFirstRun([
            {id: "tr-a", taskId: "a", state: {current: "SUCCESS"}},
            {id: "tr-b", taskId: "b", state: {current: "SUCCESS"}},
        ])
        executionsStore.replayExecution.mockResolvedValueOnce({
            id: "e2",
            state: {current: "RESTARTED"},
            taskRunList: [
                {id: "tr-a2", taskId: "a", state: {current: "SUCCESS"}},
                {id: "tr-b2", taskId: "b", state: {current: "RESTARTED"}},
            ],
        })
        executionsStore.loadLogs.mockRejectedValueOnce(new Error("network"))

        await expect(playground.runUntilTask("b")).resolves.toBeUndefined()

        expect(playground.latestExecution?.id).toBe("e2")
        expect(reused.e2).toEqual({taskRunIds: ["tr-a2"], logs: []})
    })
})
