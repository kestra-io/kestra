import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const moveFileDirectoryMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({}))
vi.mock("@kestra-io/kestra-sdk/kv", () => ({}))
vi.mock("@kestra-io/kestra-sdk/files", () => ({
    moveFileDirectory: (...args: unknown[]) => moveFileDirectoryMock(...args),
}))
vi.mock("@kestra-io/kestra-sdk/secrets", () => ({}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

const {useBaseNamespacesStore} = await import("./useBaseNamespaces")

describe("namespaces store file move", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        moveFileDirectoryMock.mockReset()
    })

    test.each(["moveFileDirectory", "renameFileDirectory"] as const)("%s sends both paths as URIs, since the endpoint rejects a raw space", async (action) => {
        await useBaseNamespacesStore()[action]({namespace: "company.team", old: "Bart Pre-Approval/coc bongo.txt", new: "Permis/coc #2.txt"})

        expect(moveFileDirectoryMock).toHaveBeenCalledWith({
            namespace: "company.team",
            from: "Bart%20Pre-Approval/coc%20bongo.txt",
            to: "Permis/coc%20%232.txt",
        })
    })
})
