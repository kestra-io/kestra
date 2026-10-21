import {afterEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"

vi.mock("./App.vue", () => ({default: {render: () => null}}))
vi.mock("./routes/routes", () => ({default: []}))
vi.mock("./utils/monacoEnvironment", () => ({}))
vi.mock("./components/plugins/TaskIcon.vue", () => ({default: {render: () => null}}))
vi.mock("./utils/serviceWorker", () => ({registerServiceWorker: vi.fn()}))
vi.mock("./utils/pwaInstallState", () => ({initPwaInstallCapture: vi.fn()}))
vi.mock("nprogress", () => ({default: {start: vi.fn(), set: vi.fn(), done: vi.fn()}}))
vi.mock("./utils/init", () => ({
    default: vi.fn(async () => {
        const piniaStore = createPinia()
        setActivePinia(piniaStore)
        return {
            router: createRouter({history: createMemoryHistory(), routes: []}),
            piniaStore,
        }
    }),
}))

afterEach(() => {
    vi.unstubAllGlobals()
    vi.doUnmock("override/utils/route")
})

describe("application API base URL", () => {
    it.each([
        ["/kestra2", "/kestra2/api/v1/main/flows/search"],
        ["http://localhost", "/api/v1/main/flows/search"],
    ])("requests flows under the configured base URL %s", async (baseUrl, expectedPath) => {
        vi.resetModules()
        vi.doMock("override/utils/route", () => ({baseUrl}))
        // Node's Request needs an absolute URL, while browsers resolve relative URLs against the page.
        const NativeRequest = globalThis.Request
        vi.stubGlobal("Request", class extends NativeRequest {
            constructor(input: RequestInfo | URL, init?: RequestInit) {
                super(typeof input === "string" ? new URL(input, window.location.href) : input, init)
            }
        })
        const fetch = vi.fn().mockResolvedValue(Response.json({results: [], total: 0}))
        vi.stubGlobal("fetch", fetch)

        await import("./main")
        const {client} = await import("@kestra-io/kestra-sdk/client")
        // The client is configured once the mocked app init resolves, so wait for that
        // rather than for a single tick, which a slow runner can miss.
        await vi.waitFor(() => expect(client.getConfig().baseUrl).toBe(baseUrl.replace(/\/$/, "")))
        const {searchFlows} = await import("@kestra-io/kestra-sdk/flows")
        const result = await searchFlows().catch((error: unknown) => error)

        expect(
            fetch,
            `searchFlows() returned ${JSON.stringify(result)} without calling the stubbed fetch`,
        ).toHaveBeenCalledOnce()
        const request = fetch.mock.calls[0][0] as Request
        expect(new URL(request.url).pathname).toBe(expectedPath)
    })
})
