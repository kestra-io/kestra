import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {reactive} from "vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

const {flowStore, executionsStore, confirmMock} = vi.hoisted(() => ({
    flowStore: {} as Record<string, unknown>,
    executionsStore: {} as Record<string, unknown>,
    confirmMock: vi.fn(),
}))

vi.mock("../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../src/stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../../src/stores/fileExplorer", () => ({useFileExplorerStore: () => ({loadNodes: vi.fn()})}))
vi.mock("../../../src/utils/toast", () => ({useToast: () => ({confirm: confirmMock})}))

const {usePlaygroundStore} = await import("../../../src/stores/playground")

describe("playground readyToStart", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        confirmMock.mockReset()
        Object.assign(flowStore, reactive({
            flow: {id: "f", namespace: "ns", revision: 1, inputs: []},
            haveChange: false,
            flowErrors: undefined,
            isCreating: false,
            saveAll: vi.fn().mockResolvedValue("saved"),
            loadGraph: vi.fn().mockResolvedValue(undefined),
        }))
        Object.assign(executionsStore, {
            execution: undefined,
            triggerExecution: vi.fn(),
        })
    })

    it("should be ready again when the create confirmation is cancelled", async () => {
        flowStore.isCreating = true
        const store = usePlaygroundStore()

        await store.runUntilTask("t1")

        expect(confirmMock).toHaveBeenCalled()
        expect(store.readyToStart).toBe(true)
    })

    it("should be ready again when triggering the execution fails", async () => {
        executionsStore.triggerExecution = vi.fn().mockRejectedValue({response: {status: 409}})
        const store = usePlaygroundStore()

        await expect(store.runUntilTask("t1")).rejects.toBeDefined()

        expect(store.readyToStart).toBe(true)
    })

    it("should stay busy while the started execution is running", async () => {
        executionsStore.triggerExecution = vi.fn().mockResolvedValue({id: "e1", state: {current: "CREATED"}})
        const store = usePlaygroundStore()

        await store.runUntilTask("t1")

        expect(store.readyToStart).toBe(false)
    })
})
