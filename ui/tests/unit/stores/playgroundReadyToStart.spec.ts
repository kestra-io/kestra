import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

const {flowStore, executionsStore} = vi.hoisted(() => ({
    flowStore: {} as Record<string, unknown>,
    executionsStore: {} as Record<string, unknown>,
}))

vi.mock("../../../src/stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../src/stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../../src/stores/fileExplorer", () => ({useFileExplorerStore: () => ({loadNodes: vi.fn()})}))

const {usePlaygroundStore} = await import("../../../src/stores/playground")

describe("playground readyToStart", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        Object.assign(flowStore, {
            flow: {id: "f", namespace: "ns", revision: 1, inputs: []},
            haveChange: false,
            flowErrors: undefined,
            isCreating: false,
            saveAsDraft: vi.fn().mockResolvedValue("saved"),
            loadGraph: vi.fn().mockResolvedValue(undefined),
        })
        Object.assign(executionsStore, {
            execution: undefined,
            triggerExecution: vi.fn(),
        })
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

    it("should stay busy while the trigger request is in flight", async () => {
        let resolveTrigger: (execution: undefined) => void = () => {}
        executionsStore.triggerExecution = vi.fn(() => new Promise((resolve) => {
            resolveTrigger = resolve
        }))
        const store = usePlaygroundStore()

        const run = store.runUntilTask("t1")
        await vi.waitFor(() => expect(executionsStore.triggerExecution).toHaveBeenCalled())

        expect(store.readyToStart).toBe(false)
        resolveTrigger(undefined)
        await run
        expect(store.readyToStart).toBe(true)
    })
})
