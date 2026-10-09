import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const autocompleteNamespacesMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({}),
}))
vi.mock("@kestra-io/kestra-sdk/namespaces", () => ({
    autocompleteNamespaces: (...args: unknown[]) => autocompleteNamespacesMock(...args),
}))
vi.mock("@kestra-io/kestra-sdk/flows", () => ({}))
vi.mock("@kestra-io/kestra-sdk/kv", () => ({}))
vi.mock("@kestra-io/kestra-sdk/files", () => ({}))
vi.mock("@kestra-io/kestra-sdk/secrets", () => ({}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

const {useBaseNamespacesStore} = await import("./useBaseNamespaces")

describe("namespaces store loadAutocomplete", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        autocompleteNamespacesMock.mockReset()
    })

    test("keeps the newest search when an older one resolves last", async () => {
        let resolveOpen: (namespaces: string[]) => void = () => {}
        let resolveSearch: (namespaces: string[]) => void = () => {}
        autocompleteNamespacesMock
            .mockReturnValueOnce(new Promise(resolve => resolveOpen = resolve))
            .mockReturnValueOnce(new Promise(resolve => resolveSearch = resolve))
        const store = useBaseNamespacesStore()

        const open = store.loadAutocomplete({q: ""})
        const search = store.loadAutocomplete({q: "ns300"})
        resolveSearch(["company.ns300"])
        await search
        resolveOpen(["company.ns001", "company.ns002"])

        expect(await open).toEqual(["company.ns001", "company.ns002"])
        expect(store.autocomplete.value).toEqual(["company.ns300"])
    })
})
