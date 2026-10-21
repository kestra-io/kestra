import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import type * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import type * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

const {flowApi, executionApi, clientGet, trackBlueprintSelection} = vi.hoisted(() => ({
    flowApi: vi.fn(),
    executionApi: vi.fn(),
    clientGet: vi.fn(),
    trackBlueprintSelection: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: clientGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flow: (...args: Parameters<typeof FlowsAPI.flow>) => flowApi(...args),
    validateFlows: vi.fn(() => Promise.resolve([{}])),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: Parameters<typeof ExecutionsAPI.execution>) => executionApi(...args),
}))

vi.mock("../utils/toast", () => ({
    makeToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition: "OSS", version: "1.3.0"}}),
}))

vi.mock("../utils/tabTracking", () => ({
    trackBlueprintSelection,
}))

// static imports: the store modules drag in heavy singletons (e.g. Monaco), which re-registering
// through vi.resetModules() would throw on
const {useFlowStore} = await import("./flow")
const {useExecutionsStore} = await import("./executions")
const {useBlueprintsStore} = await import("./blueprints")

describe("entity load ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        flowApi.mockReset()
        executionApi.mockReset()
        clientGet.mockReset()
        trackBlueprintSelection.mockReset()
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

    it("does not drop a successful flow load when a later, same-flow load fails", async () => {
        const store = useFlowStore()
        let resolveFirst: (flow: unknown) => void = () => {}
        let callCount = 0
        flowApi.mockImplementation(() => {
            callCount++
            return callCount === 1
                ? new Promise((resolve) => {
                    resolveFirst = resolve
                })
                : Promise.reject(new Error("network error"))
        })

        const first = store.loadFlow({namespace: "ns", id: "target"})
        await store.loadFlow({namespace: "ns", id: "target"}).catch(() => {})
        resolveFirst({id: "target", namespace: "ns", revision: 1, source: "id: target"})
        await first

        expect(store.flow?.id).toBe("target")
        expect(store.flowYaml).toBe("id: target")
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

    it("drops a community blueprint load the user has navigated away from", async () => {
        const store = useBlueprintsStore()
        let resolveSlow: (response: unknown) => void = () => {}
        clientGet.mockImplementation((url: string) => url.includes("slow")
            ? new Promise((resolve) => {
                resolveSlow = resolve
            })
            : Promise.resolve({data: {id: "fast"}}))

        const slow = store.getBlueprint({type: "community", kind: "flow", id: "slow"})
        await store.getBlueprint({type: "community", kind: "flow", id: "fast"})
        resolveSlow({data: {id: "slow"}})
        await slow

        expect(store.blueprint?.id).toBe("fast")
        expect(trackBlueprintSelection).toHaveBeenCalledTimes(1)
        expect(trackBlueprintSelection).toHaveBeenCalledWith("fast")
    })

    it("drops a custom blueprint load the user has navigated away from", async () => {
        const store = useBlueprintsStore()
        let resolveSlow: (response: unknown) => void = () => {}
        clientGet.mockImplementation((url: string) => url.includes("slow")
            ? new Promise((resolve) => {
                resolveSlow = resolve
            })
            : Promise.resolve({data: {id: "fast"}}))

        const slow = store.getBlueprint({type: "custom", id: "slow"})
        await store.getBlueprint({type: "custom", id: "fast"})
        resolveSlow({data: {id: "slow"}})
        await slow

        expect(store.blueprint?.id).toBe("fast")
        expect(trackBlueprintSelection).toHaveBeenCalledTimes(1)
        expect(trackBlueprintSelection).toHaveBeenCalledWith("fast")
    })
})
