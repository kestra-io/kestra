import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const fileRevisionsMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({}))
vi.mock("@kestra-io/kestra-sdk/kv", () => ({}))
vi.mock("@kestra-io/kestra-sdk/files", () => ({
    fileRevisions: (...args: unknown[]) => fileRevisionsMock(...args),
}))
vi.mock("@kestra-io/kestra-sdk/secrets", () => ({}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

const {useBaseNamespacesStore} = await import("./useBaseNamespaces")

describe("namespaces store fileRevisions", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        fileRevisionsMock.mockReset()
    })

    test("returns the revisions the server numbered and leaves out an entry without a number", async () => {
        fileRevisionsMock.mockResolvedValue([{revision: 3}, {}, {revision: 1}])

        await expect(useBaseNamespacesStore().fileRevisions({namespace: "io.kestra.test", path: "main.py"}))
            .resolves.toEqual([{revision: 3}, {revision: 1}])
    })

    test("returns nothing without asking the server when there is no path", async () => {
        await expect(useBaseNamespacesStore().fileRevisions({namespace: "io.kestra.test", path: ""})).resolves.toEqual([])

        expect(fileRevisionsMock).not.toHaveBeenCalled()
    })
})
