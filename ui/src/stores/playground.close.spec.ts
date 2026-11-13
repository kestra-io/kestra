import {beforeEach, describe, expect, it, vi} from "vitest"
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
    endPlaygroundRun: vi.fn(),
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

describe("closing the playground panel", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        playgroundApi.listReusedTaskRuns.mockResolvedValue({taskRuns: {}})
        playgroundApi.endPlaygroundRun.mockResolvedValue(undefined)
    })

    it("ends the run it shows", async () => {
        const playground = await panelShowing("BREAKPOINT")

        playground.enabled = false

        expect(playgroundApi.endPlaygroundRun).toHaveBeenCalledWith(expect.objectContaining({executionId: "e1"}))
    })

    it("leaves the run alone when the editor is left, as a reload would", async () => {
        const playground = await panelShowing("BREAKPOINT")

        playground.leaveEditor()

        expect(playgroundApi.endPlaygroundRun).not.toHaveBeenCalled()
    })

    it("ends nothing when the run already ended", async () => {
        const playground = await panelShowing("SUCCESS")

        playground.enabled = false

        expect(playgroundApi.endPlaygroundRun).not.toHaveBeenCalled()
    })
})
