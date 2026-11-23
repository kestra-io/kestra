import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {flushPromises} from "@vue/test-utils"

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

function pendingRequest() {
    let resolve: () => void = () => {}
    const promise = new Promise<void>((done) => resolve = done)
    return {promise, resolve}
}

describe("namespaces store saveOrCreateFile", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        postMock.mockReset()
        postMock.mockResolvedValue({data: undefined})
    })

    const save = (path: string, content: string) => useBaseNamespacesStore().saveOrCreateFile({namespace: "io.kestra.test", path, content})

    test("sends a save of a file only once the previous save of that file has returned", async () => {
        const first = pendingRequest()
        postMock.mockReturnValueOnce(first.promise)

        const firstSave = save("data.txt", "v1")
        const secondSave = save("/data.txt", "v2")
        await flushPromises()
        expect(postMock).toHaveBeenCalledTimes(1)

        first.resolve()
        await Promise.all([firstSave, secondSave])
        expect(postMock).toHaveBeenCalledTimes(2)
        expect(await ((postMock.mock.calls[1][1] as FormData).get("fileContent") as Blob).text()).toBe("v2")
    })

    test("does not send a save again while the same content of that file is already on its way", async () => {
        const first = pendingRequest()
        postMock.mockReturnValueOnce(first.promise)

        const saves = [save("data.txt", "v1"), save("data.txt", "v1"), save("data.txt", "v1")]
        first.resolve()
        await Promise.all(saves)

        expect(postMock).toHaveBeenCalledTimes(1)
    })

    test("still sends the next save of a file when the previous one failed", async () => {
        postMock.mockRejectedValueOnce(new Error("timeout"))

        const firstSave = save("data.txt", "v1")
        const secondSave = save("data.txt", "v2")

        await expect(firstSave).rejects.toThrow("timeout")
        await secondSave
        expect(postMock).toHaveBeenCalledTimes(2)
    })

    test("saves different files in parallel", async () => {
        const first = pendingRequest()
        const second = pendingRequest()
        postMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

        const saves = [save("a.txt", "a"), save("b.txt", "b")]
        await flushPromises()
        expect(postMock).toHaveBeenCalledTimes(2)

        first.resolve()
        second.resolve()
        await Promise.all(saves)
    })
})
