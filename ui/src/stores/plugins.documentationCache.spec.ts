import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {usePluginsStore} from "./plugins"

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

vi.mock("./api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("../utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const CLS = "io.kestra.plugin.core.log.Log"

describe("plugins store documentation cache", () => {
    let store: ReturnType<typeof usePluginsStore>

    beforeEach(async () => {
        vi.resetModules()
        pluginDocumentationMock.mockReset()
        pluginDocumentationMock.mockResolvedValue({schema: {properties: {properties: {}}}})
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("./plugins")
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
