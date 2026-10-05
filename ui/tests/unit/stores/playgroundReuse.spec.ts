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
    saveAll: vi.fn(() => Promise.resolve()),
    loadGraph: vi.fn(() => Promise.resolve(graph)),
    loadFlow: vi.fn(() => Promise.resolve(previousFlow)),
})

const executionsStore = reactive({
    execution: undefined as Record<string, unknown> | undefined,
    flow: undefined,
    triggerExecution: vi.fn(),
    replayExecution: vi.fn(),
})

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))
vi.mock("../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../src/stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../../src/stores/fileExplorer", () => ({useFileExplorerStore: () => ({loadNodes: vi.fn()})}))
vi.mock("../../../src/utils/toast", () => ({useToast: () => ({confirm: vi.fn(), error: vi.fn()})}))
vi.mock("@kestra-io/topology/vue-flow-utils", () => ({
    areTasksIdenticalInGraphUntilTask: () => true,
    getNextTaskNodes: () => [],
}))

const runningExecution = {id: "e1", flowRevision: 1, state: {current: "CREATED"}}

async function playgroundAfterFirstRun() {
    const {usePlaygroundStore} = await import("../../../src/stores/playground")
    const playground = usePlaygroundStore()

    executionsStore.triggerExecution.mockResolvedValueOnce(runningExecution)
    flowStore.flow = flowAt(1, "hello")
    await playground.runUntilTask("b")

    executionsStore.execution = {...runningExecution, state: {current: "SUCCESS"}, taskRunList: [{id: "tr-b", taskId: "b"}]}
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
})
