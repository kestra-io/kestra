import {describe, it, expect, vi, beforeAll, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

// Bulk restart goes through the generated SDK (ExecutionsAPI.restartExecutionsBy*), not through
// useClient()'s axios-like facade, so assert at the fetch boundary the generated client actually
// uses. configureClient() is what installs the real querySerializer — the one that knows how to
// turn a QueryFilter[] into PHP-style `filters[field][OP]` params — so going through it keeps this
// test honest about what the app sends on the wire.
// Typed with varargs so `fetchSpy.mock.calls[0]?.[0]` type-checks; an argless `vi.fn(() => …)`
// infers zero-length call tuples and trips TS2493 under vue-tsc.
const fetchSpy = vi.fn(async (..._args: any[]) => new Response(JSON.stringify({count: 1}), {
    status: 200,
    headers: {"content-type": "application/json"},
}))

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

// Importing the executions store pulls in a large dependency graph (design system,
// monaco), so import it once and reuse it rather than resetting modules per test.
let useExecutionsStore: typeof import("../../../src/stores/executions")["useExecutionsStore"]

/** The Request the generated client handed to fetch, plus its parsed URL. */
function lastRequest() {
    const request = fetchSpy.mock.calls[0]?.[0] as unknown as Request
    return {request, url: new URL(request.url)}
}

describe("executions store — bulk restart revision forwarding", () => {
    beforeAll(async () => {
        // A baseUrl is required: the generated client builds an absolute URL, and a relative one
        // fails to parse under jsdom. `fetch` is injected rather than stubbed globally so the spy
        // only ever sees SDK traffic.
        const {configureClient} = await import("@kestra-io/kestra-sdk")
        configureClient({baseUrl: "http://localhost", fetch: fetchSpy})
        ;({useExecutionsStore} = await import("../../../src/stores/executions"))
    }, 60000)

    beforeEach(() => {
        fetchSpy.mockClear()
        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("forwards latestRevision as a query param for restart by-ids", async () => {
        const store = useExecutionsStore()

        await store.bulkRestartExecution({executionsId: ["exec-1", "exec-2"], latestRevision: true})

        expect(fetchSpy).toHaveBeenCalledTimes(1)
        const {request, url} = lastRequest()
        expect(url.pathname).toBe("/api/v1/main/executions/restart/by-ids")
        // the revision choice is forwarded as a query param, not smuggled into the body
        expect(url.searchParams.get("latestRevision")).toBe("true")
        // the execution ids stay in the request body
        expect(await request.text()).toBe(JSON.stringify(["exec-1", "exec-2"]))
    })

    it("forwards latestRevision=false (original revision) by-ids", async () => {
        const store = useExecutionsStore()

        await store.bulkRestartExecution({executionsId: ["exec-1"], latestRevision: false})

        // false must survive as an explicit value — dropping it would silently mean "latest"
        expect(lastRequest().url.searchParams.get("latestRevision")).toBe("false")
    })

    it("forwards latestRevision as a query param for restart by-query", async () => {
        const store = useExecutionsStore()

        await store.queryRestartExecution({latestRevision: true, "filters[namespace][PREFIX]": "io.kestra.tests"})

        expect(fetchSpy).toHaveBeenCalledTimes(1)
        const {url} = lastRequest()
        expect(url.pathname).toBe("/api/v1/main/executions/restart/by-query")
        expect(url.searchParams.get("latestRevision")).toBe("true")
        // latestRevision is peeled off before the rest becomes filters, so it never leaks in as one
        expect(url.searchParams.get("filters[namespace][PREFIX]")).toBe("io.kestra.tests")
    })
})
