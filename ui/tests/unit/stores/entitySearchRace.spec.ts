import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

const {flowSearch, executionSearch} = vi.hoisted(() => ({flowSearch: vi.fn(), executionSearch: vi.fn()}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlows: (params?: unknown) => flowSearch(params),
    flow: vi.fn(),
    validateFlows: vi.fn(() => Promise.resolve([{}])),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    searchExecutions: (params?: unknown) => executionSearch(params),
    execution: vi.fn(),
}))

vi.mock("../../../src/utils/toast", () => ({
    makeToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
}))

// static imports: the store modules drag in heavy singletons (e.g. Monaco), which re-registering
// through vi.resetModules() would throw on
const {useFlowStore} = await import("../../../src/stores/flow")
const {useExecutionsStore} = await import("../../../src/stores/executions")

const page = (ids: string[]) => ({results: ids.map(id => ({id, namespace: "ns"})), total: ids.length})

describe("list search ordering", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        flowSearch.mockReset()
        executionSearch.mockReset()
    })

    it("drops an executions search that answers after a newer one", async () => {
        const store = useExecutionsStore()

        let resolveStale: (value: unknown) => void = () => {}
        let first = true
        executionSearch.mockImplementation(() => {
            if (first) {
                first = false
                return new Promise((resolve) => {
                    resolveStale = resolve
                })
            }
            return Promise.resolve(page(["newest"]))
        })

        const stale = store.findExecutions({page: 1, size: 25})
        const newest = store.findExecutions({page: 1, size: 25})
        resolveStale(page(["stale"]))
        await Promise.all([stale, newest])

        expect(store.executions?.map(execution => execution.id)).toEqual(["newest"])
        expect(store.total).toBe(1)
    })

    it("drops a flows search that answers after a newer one", async () => {
        const store = useFlowStore()

        let resolveStale: (value: unknown) => void = () => {}
        let first = true
        flowSearch.mockImplementation(() => {
            if (first) {
                first = false
                return new Promise((resolve) => {
                    resolveStale = resolve
                })
            }
            return Promise.resolve(page(["newest"]))
        })

        const stale = store.findFlows({page: 1, size: 25})
        const newest = store.findFlows({page: 1, size: 25})
        resolveStale(page(["stale"]))
        await Promise.all([stale, newest])

        expect(store.flows?.map(flow => flow.id)).toEqual(["newest"])
        expect(store.total).toBe(1)
    })

    it("still commits an in-flight executions search when a non-committing fetch runs in between", async () => {
        const store = useExecutionsStore()

        let resolveSearch: (value: unknown) => void = () => {}
        executionSearch.mockImplementationOnce(() => new Promise((resolve) => {
            resolveSearch = resolve
        })).mockImplementationOnce(() => Promise.resolve(page(["drill-down"])))

        const inFlight = store.findExecutions({page: 1, size: 25})
        await store.findExecutions({page: 1, size: 25, commit: false})
        resolveSearch(page(["list"]))
        await inFlight

        expect(store.executions?.map(execution => execution.id)).toEqual(["list"])
    })

    it("still commits an in-flight flows search when a non-committing fetch runs in between", async () => {
        const store = useFlowStore()

        let resolveSearch: (value: unknown) => void = () => {}
        flowSearch.mockImplementationOnce(() => new Promise((resolve) => {
            resolveSearch = resolve
        })).mockImplementationOnce(() => Promise.resolve(page(["drill-down"])))

        const inFlight = store.findFlows({page: 1, size: 25})
        await store.findFlows({page: 1, size: 25, commit: false})
        resolveSearch(page(["list"]))
        await inFlight

        expect(store.flows?.map(flow => flow.id)).toEqual(["list"])
    })

    it("still commits an in-flight flows search when a flows count runs in between", async () => {
        const store = useFlowStore()

        let resolveSearch: (value: unknown) => void = () => {}
        flowSearch.mockImplementationOnce(() => new Promise((resolve) => {
            resolveSearch = resolve
        })).mockImplementationOnce(() => Promise.resolve(page(["count"])))

        const inFlight = store.findFlows({page: 1, size: 25})
        await store.findFlows({size: 1, onlyTotal: true})
        resolveSearch(page(["list"]))
        await inFlight

        expect(store.flows?.map(flow => flow.id)).toEqual(["list"])
    })
})
