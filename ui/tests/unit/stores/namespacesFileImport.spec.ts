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

const {useBaseNamespacesStore} = await import("../../../src/composables/useBaseNamespaces")

describe("namespaces store importFileDirectory", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        postMock.mockReset()
        postMock.mockResolvedValue({data: undefined})
    })

    async function upload(file: File, path = file.name) {
        await useBaseNamespacesStore().importFileDirectory({namespace: "io.kestra.test", path, file})
        const [url, body] = postMock.mock.calls[0] as [string, FormData]
        return {url, part: body.get("fileContent") as File}
    }

    test("keeps the file name on the multipart part so the server can unpack a zip", async () => {
        const {url, part} = await upload(new File(["PK"], "io.kestra.test_files.zip", {type: "application/zip"}))

        expect(part.name).toBe("io.kestra.test_files.zip")
        expect(part.type).toBe("application/zip")
        expect(url).toBe("http://localhost:8080/api/v1/main/namespaces/io.kestra.test/files?path=/io.kestra.test_files.zip")
    })

    test("uploads a nested file to its relative path", async () => {
        const {url, part} = await upload(new File(["print(1)"], "main.py"), "data/scripts/main.py")

        expect(part.name).toBe("main.py")
        expect(url).toBe("http://localhost:8080/api/v1/main/namespaces/io.kestra.test/files?path=/data/scripts/main.py")
    })

    test("treats a comma in the file name as part of the name, not as a path separator", async () => {
        const {url} = await upload(new File(["a,b"], "report,v2.csv"))

        expect(url).toBe("http://localhost:8080/api/v1/main/namespaces/io.kestra.test/files?path=/report%2Cv2.csv")
    })
})
