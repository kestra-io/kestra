import {describe, it, expect, vi, beforeAll, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const axiosPost = vi.fn().mockResolvedValue({data: {count: 1}})

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        replace: vi.fn(),
        push: vi.fn(),
    }),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))

// The store resolves its HTTP client via useClient(); point its post at our spy.
vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: axiosPost,
    }),
}))

// Importing the executions store pulls in a large dependency graph (design system,
// monaco), so import it once and reuse it rather than resetting modules per test.
let useExecutionsStore: typeof import("../../../src/stores/executions")["useExecutionsStore"]

describe("executions store — bulk restart revision forwarding", () => {
    beforeAll(async () => {
        ({useExecutionsStore} = await import("../../../src/stores/executions"))
    }, 60000)

    beforeEach(() => {
        axiosPost.mockClear()
        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("forwards latestRevision as a query param for restart by-ids", async () => {
        const store = useExecutionsStore()

        await store.bulkRestartExecution({executionsId: ["exec-1", "exec-2"], latestRevision: true})

        expect(axiosPost).toHaveBeenCalledTimes(1)
        const [url, body, config] = axiosPost.mock.calls[0]
        expect(url).toBe("/api/v1/main/executions/restart/by-ids")
        // the execution ids stay in the request body
        expect(body).toEqual(["exec-1", "exec-2"])
        // the revision choice is forwarded as a query param (was previously dropped)
        expect(config?.params?.latestRevision).toBe(true)
    })

    it("forwards latestRevision=false (original revision) by-ids", async () => {
        const store = useExecutionsStore()

        await store.bulkRestartExecution({executionsId: ["exec-1"], latestRevision: false})

        const [, , config] = axiosPost.mock.calls[0]
        expect(config?.params?.latestRevision).toBe(false)
    })

    it("forwards latestRevision as a query param for restart by-query", async () => {
        const store = useExecutionsStore()

        await store.queryRestartExecution({latestRevision: true, "filters[namespace][PREFIX]": "io.kestra.tests"})

        expect(axiosPost).toHaveBeenCalledTimes(1)
        const [url, , config] = axiosPost.mock.calls[0]
        expect(url).toBe("/api/v1/main/executions/restart/by-query")
        expect(config?.params?.latestRevision).toBe(true)
    })
})
