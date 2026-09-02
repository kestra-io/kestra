import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

const {flowApi, executionApi} = vi.hoisted(() => ({flowApi: vi.fn(), executionApi: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flow: (...args: any[]) => flowApi(...args),
    validateFlows: vi.fn(() => Promise.resolve([{}])),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: any[]) => executionApi(...args),
}))

vi.mock("../../../src/utils/toast", () => ({
    makeToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
}))

// static imports: the store modules drag in heavy singletons (e.g. Monaco), which re-registering
// through vi.resetModules() would throw on
const {useFlowStore} = await import("../../../src/stores/flow")
const {useExecutionsStore} = await import("../../../src/stores/executions")

describe("entity load ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        flowApi.mockReset()
        executionApi.mockReset()
    })

    it("drops a flow load the user has navigated away from", async () => {
        const store = useFlowStore()
        let resolveSlow: (flow: unknown) => void = () => {}
        flowApi.mockImplementation((params: {id: string}) => params.id === "slow"
            ? new Promise((resolve) => {
                resolveSlow = resolve
            })
            : Promise.resolve({id: "fast", namespace: "ns", revision: 1, source: "id: fast"}))

        const slow = store.loadFlow({namespace: "ns", id: "slow"})
        await store.loadFlow({namespace: "ns", id: "fast"})
        resolveSlow({id: "slow", namespace: "ns", revision: 1, source: "id: slow"})
        await slow

        expect(store.flow?.id).toBe("fast")
        expect(store.flowYaml).toBe("id: fast")
    })

    it("drops an execution load the user has navigated away from", async () => {
        const store = useExecutionsStore()
        let resolveSlow: (execution: unknown) => void = () => {}
        executionApi.mockImplementation((params: {executionId: string}) => params.executionId === "slow"
            ? new Promise((resolve) => {
                resolveSlow = resolve
            })
            : Promise.resolve({id: "fast"}))

        const slow = store.loadExecution({id: "slow"})
        await store.loadExecution({id: "fast"})
        resolveSlow({id: "slow"})
        await slow

        expect(store.execution?.id).toBe("fast")
    })
})
