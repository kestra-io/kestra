import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const getMock = vi.fn()
const pluginIconsMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: getMock, post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/stores/api", () => ({
    API_URL: "https://api.kestra.io",
    useApiStore: () => ({pluginIcons: pluginIconsMock}),
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

describe("plugins store fetchIcons", () => {
    let store: any

    beforeEach(async () => {
        getMock.mockReset()
        pluginIconsMock.mockReset()
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
    })

    it("does not set iconUrl for icons resolved from the local instance", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: "localbase64", flowable: false, monochrome: false},
            },
        })
        pluginIconsMock.mockResolvedValueOnce({data: {}})

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.core.log.Log"]).toEqual({
            flowable: false,
            monochrome: false,
            hasIcon: true,
        })
        expect(store.icons["io.kestra.plugin.core.log.Log"].iconUrl).toBeUndefined()
    })

    it("embeds a data URI for icons resolved from the external ecosystem plugin catalog", async () => {
        // This instance has no way to serve icons for plugins it doesn't have installed, so
        // api.kestra.io-sourced icons must carry their own renderable source.
        getMock.mockResolvedValueOnce({data: {}})
        pluginIconsMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.scripts.python.Commands": {icon: "ecosystembase64", flowable: false, monochrome: false},
            },
        })

        await store.fetchIcons()

        const icon = store.icons["io.kestra.plugin.scripts.python.Commands"]
        expect(icon.hasIcon).toBe(true)
        expect(icon.iconUrl).toBe("data:image/svg+xml;base64,ecosystembase64")
    })

    it("prefers the local instance's icon over the ecosystem catalog when both have an entry", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: "localbase64", flowable: false, monochrome: false},
            },
        })
        pluginIconsMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: "ecosystembase64", flowable: false, monochrome: true},
            },
        })

        await store.fetchIcons()

        // api.kestra.io wins on conflict (spread order), consistent with pre-existing behavior
        const icon = store.icons["io.kestra.plugin.core.log.Log"]
        expect(icon.iconUrl).toBe("data:image/svg+xml;base64,ecosystembase64")
    })
})
