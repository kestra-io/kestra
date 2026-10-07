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

const downloadFileFromExecution = vi.fn()

vi.mock("@kestra-io/kestra-sdk/executions", () => ({downloadFileFromExecution}))

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("../../../src/stores/executions")

describe("executions store fileContent", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        downloadFileFromExecution.mockReset()
    })

    test("returns the whole file verbatim, without parsing a body that looks like JSON", async () => {
        const jsonLike = "{\"not\":\"parsed\"}"
        downloadFileFromExecution.mockResolvedValue(new Blob([jsonLike]))
        const store = useExecutionsStore()

        const content = await store.fileContent({executionId: "exec-1", path: "kestra:///outputs/report.html"})

        expect(content).toBe(jsonLike)
        expect(downloadFileFromExecution).toHaveBeenCalledWith({executionId: "exec-1", path: "kestra:///outputs/report.html"})
    })

    test("propagates a request failure so callers can surface an error state", async () => {
        downloadFileFromExecution.mockRejectedValue(new Error("boom"))
        const store = useExecutionsStore()

        await expect(store.fileContent({executionId: "exec-1", path: "kestra:///outputs/report.html"}))
            .rejects.toThrow("boom")
    })
})
