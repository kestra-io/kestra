import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const flowStore = {
    isCreating: false,
    haveChange: true,
    flowErrors: undefined as unknown,
    flow: {id: "f", namespace: "ns", revision: 3, inputs: []} as Record<string, unknown>,
    saveAll: vi.fn(),
    saveAsDraft: vi.fn(),
    loadGraph: vi.fn(),
}
const triggerExecution = vi.fn()
const push = vi.fn()

vi.mock("../../../src/stores/flow", () => ({
    useFlowStore: () => flowStore,
    isSuccessfulFlowSaveOutcome: (o: string) => o === "saved" || o === "redirect_to_update",
}))

vi.mock("../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({triggerExecution}),
}))

vi.mock("../../../src/stores/fileExplorer", () => ({
    useFileExplorerStore: () => ({loadNodes: vi.fn()}),
}))

vi.mock("@kestra-io/design-system", () => ({
    State: {KILLING: "KILLING", RUNNING: "RUNNING", RESTARTED: "RESTARTED", CREATED: "CREATED"},
    isDeepEqual: () => true,
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tenant: "main"}, query: {}}),
    useRouter: () => ({push, replace: vi.fn()}),
}))

const loadStore = async () => {
    const {usePlaygroundStore} = await import("../../../src/stores/playground")
    return usePlaygroundStore()
}

describe("playground run saves a draft", () => {
    beforeEach(() => {
        vi.resetModules()
        vi.clearAllMocks()
        setActivePinia(createPinia())
        flowStore.isCreating = false
        flowStore.saveAsDraft.mockResolvedValue("saved")
        flowStore.loadGraph.mockResolvedValue(undefined)
        triggerExecution.mockResolvedValue({id: "e1", state: {current: "CREATED"}})
        push.mockResolvedValue(undefined)
    })

    it("saves unsaved edits as a draft rather than publishing them", async () => {
        const store = await loadStore()

        await store.runUntilTask("a")

        expect(flowStore.saveAsDraft).toHaveBeenCalledOnce()
        expect(flowStore.saveAll).not.toHaveBeenCalled()
        expect(triggerExecution).toHaveBeenCalledWith(expect.objectContaining({kind: "PLAYGROUND", revision: 3}))
    })

    it("saves a new flow as a draft without a dialog and reopens the playground on its edit page", async () => {
        flowStore.isCreating = true
        flowStore.saveAsDraft.mockResolvedValue("redirect_to_update")
        const store = await loadStore()
        store.enabled = false

        await store.runUntilTask("a")

        expect(flowStore.saveAsDraft).toHaveBeenCalledOnce()
        expect(flowStore.saveAll).not.toHaveBeenCalled()
        expect(push).toHaveBeenCalledWith(expect.objectContaining({
            name: "flows/update/edit",
            query: expect.objectContaining({playground: "on", runUntilTaskId: "a"}),
        }))
        expect(store.enabled).toBe(true)
        expect(store.readyToStart).toBe(true)
    })

    it("lets the user retry when saving a new flow fails", async () => {
        flowStore.isCreating = true
        flowStore.saveAsDraft.mockRejectedValue(new Error("invalid"))
        const store = await loadStore()

        await expect(store.runUntilTask("a")).rejects.toThrow("invalid")

        expect(push).not.toHaveBeenCalled()
        expect(store.readyToStart).toBe(true)
    })
})
