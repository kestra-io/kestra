import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {nextTick, reactive} from "vue"

const flowStore = reactive({
    flow: {id: "f", namespace: "ns", revision: 1, inputs: []} as Record<string, unknown>,
    haveChange: false,
    flowErrors: undefined,
    isCreating: false,
})

const executionsStore = reactive({
    execution: undefined as Record<string, unknown> | undefined,
    clearReusedTaskRuns: vi.fn(),
})

const playgroundApi = vi.hoisted(() => ({
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

async function panelShowing(state: string) {
    const {usePlaygroundStore} = await import("./playground")
    const playground = usePlaygroundStore()
    playground.enabled = true
    playgroundApi.listPlaygroundRuns.mockResolvedValue({results: [{id: "e1", state: {current: state}}], total: 1})
    await playground.loadSession()
    await nextTick()
    return playground
}

describe("playground panel reports", () => {
    afterAll(() => {
        sessionStorage.removeItem("playgroundClientId")
    })

    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
        vi.clearAllMocks()
        playgroundApi.listReusedTaskRuns.mockResolvedValue({taskRuns: {}})
        playgroundApi.reportPlaygroundClient.mockResolvedValue({reportEvery: "PT5S"})
        playgroundApi.releasePlaygroundClient.mockResolvedValue({})
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("keeps reporting a run at its breakpoint, as often as the server asks", async () => {
        await panelShowing("BREAKPOINT")
        expect(playgroundApi.reportPlaygroundClient).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(5000)

        expect(playgroundApi.reportPlaygroundClient).toHaveBeenCalledTimes(2)
        expect(playgroundApi.reportPlaygroundClient).toHaveBeenLastCalledWith(expect.objectContaining({executionId: "e1", clientId: expect.any(String)}))
    })

    it("stops reporting once the run ended", async () => {
        const playground = await panelShowing("BREAKPOINT")

        playground.executions[0] = {...playground.executions[0], state: {current: "KILLED"}} as typeof playground.executions[0]
        await nextTick()
        await vi.advanceTimersByTimeAsync(20000)

        expect(playgroundApi.reportPlaygroundClient).toHaveBeenCalledTimes(1)
    })

    it("releases the run when the panel is closed", async () => {
        const playground = await panelShowing("BREAKPOINT")

        playground.enabled = false

        expect(playgroundApi.releasePlaygroundClient).toHaveBeenCalledWith(expect.objectContaining({executionId: "e1"}))
    })

    it("leaves the run to the server's timeout when the editor is left, as a reload would", async () => {
        const playground = await panelShowing("BREAKPOINT")

        playground.leaveEditor()
        await vi.advanceTimersByTimeAsync(20000)

        expect(playgroundApi.releasePlaygroundClient).not.toHaveBeenCalled()
        expect(playgroundApi.reportPlaygroundClient).toHaveBeenCalledTimes(1)
    })

    it("reports nothing for a run that already ended", async () => {
        await panelShowing("SUCCESS")

        expect(playgroundApi.reportPlaygroundClient).not.toHaveBeenCalled()
    })
})
