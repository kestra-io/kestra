import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const postMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: (...args: unknown[]) => postMock(...args),
        put: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({}))
vi.mock("@kestra-io/kestra-sdk/kv", () => ({}))
vi.mock("@kestra-io/kestra-sdk/files", () => ({}))
vi.mock("@kestra-io/kestra-sdk/secrets", () => ({}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

const {useBaseNamespacesStore} = await import("./useBaseNamespaces")

describe("namespaces store saveOrCreateFile", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        postMock.mockReset()
        postMock.mockResolvedValue({data: undefined})
    })

    test("saves to the file whose name holds characters reserved in a query string", async () => {
        await useBaseNamespacesStore().saveOrCreateFile({namespace: "io.kestra.test", path: "dir/a&b #1.txt", content: "v1"})

        expect(postMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/main/namespaces/io.kestra.test/files?path=/dir/a%26b%20%231.txt")
    })
})
