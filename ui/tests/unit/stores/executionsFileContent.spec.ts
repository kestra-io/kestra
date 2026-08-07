import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}))

const getMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: (...args: unknown[]) => getMock(...args),
        post: vi.fn(),
        put: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("../../../src/stores/executions")

describe("executions store fileContent", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        getMock.mockReset()
    })

    test("requests the raw /file endpoint as text so the full document is returned untruncated", async () => {
        getMock.mockResolvedValue({data: "<html><body>full</body></html>"})
        const store = useExecutionsStore()

        const content = await store.fileContent({executionId: "exec-1", path: "kestra:///outputs/report.html"})

        expect(content).toBe("<html><body>full</body></html>")

        const [url, config] = getMock.mock.calls[0] as [string, Record<string, any>]
        // Hits /file (full bytes), NOT /file/preview (row/byte capped).
        expect(url).toBe("http://localhost:8080/api/v1/main/executions/exec-1/file")
        expect(url).not.toContain("/preview")
        expect(config.params).toEqual({path: "kestra:///outputs/report.html"})
        expect(config.responseType).toBe("text")
    })

    test("uses an identity transformResponse so axios does not JSON-parse the HTML body", async () => {
        getMock.mockResolvedValue({data: "raw"})
        const store = useExecutionsStore()

        await store.fileContent({executionId: "exec-1", path: "kestra:///outputs/page.html"})

        const config = (getMock.mock.calls[0] as [string, Record<string, any>])[1]
        const transform = Array.isArray(config.transformResponse) ? config.transformResponse[0] : config.transformResponse
        // A raw JSON-looking string must be returned verbatim, not parsed into an object.
        const jsonLike = "{\"not\":\"parsed\"}"
        expect(transform(jsonLike)).toBe(jsonLike)
    })

    test("propagates a request failure so callers can surface an error state", async () => {
        getMock.mockRejectedValue(new Error("boom"))
        const store = useExecutionsStore()

        await expect(store.fileContent({executionId: "exec-1", path: "kestra:///outputs/report.html"}))
            .rejects.toThrow("boom")
    })
})
