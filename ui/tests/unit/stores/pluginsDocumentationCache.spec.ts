import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const pluginDocumentationMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginDocumentation: pluginDocumentationMock,
    pluginDocumentationFromVersion: vi.fn(),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/stores/api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const CLS = "io.kestra.plugin.core.log.Log"

describe("plugins store documentation cache", () => {
    let store: any

    beforeEach(async () => {
        vi.resetModules()
        pluginDocumentationMock.mockReset()
        pluginDocumentationMock.mockResolvedValue({schema: {properties: {properties: {}}}})
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
    })

    it("caches an all-properties lookup so repeated completions do not refetch it", async () => {
        await store.load({cls: CLS, commit: false, all: true})
        await store.load({cls: CLS, commit: false, all: true})

        expect(pluginDocumentationMock).toHaveBeenCalledTimes(1)
    })

    it("keeps the all-properties and default documentation as separate cache entries", async () => {
        await store.load({cls: CLS, commit: false, all: true})
        await store.load({cls: CLS, commit: false})

        expect(pluginDocumentationMock).toHaveBeenCalledTimes(2)
        expect(pluginDocumentationMock.mock.calls[0][0]).toMatchObject({cls: CLS, all: true})
        expect(pluginDocumentationMock.mock.calls[1][0].all).toBeUndefined()
    })
})
