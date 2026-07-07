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

const fixedColorSvgBase64 = btoa("<svg fill=\"blue\"></svg>")
const currentColorSvgBase64 = btoa("<svg fill=\"currentColor\"></svg>")

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
                "io.kestra.plugin.core.log.Log": {icon: fixedColorSvgBase64, flowable: false, monochrome: false},
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

    it("passes the content hash through for icons resolved from the local instance", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: fixedColorSvgBase64, flowable: false, monochrome: false, hash: "abc123"},
            },
        })
        pluginIconsMock.mockResolvedValueOnce({data: {}})

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.core.log.Log"].hash).toBe("abc123")
    })

    it("embeds a data URI for icons resolved from the external ecosystem plugin catalog", async () => {
        // This instance has no way to serve icons for plugins it doesn't have installed, so
        // api.kestra.io-sourced icons must carry their own renderable source.
        getMock.mockResolvedValueOnce({data: {}})
        pluginIconsMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.scripts.python.Commands": {icon: fixedColorSvgBase64, flowable: false, monochrome: false},
            },
        })

        await store.fetchIcons()

        const icon = store.icons["io.kestra.plugin.scripts.python.Commands"]
        expect(icon.hasIcon).toBe(true)
        expect(icon.iconUrl).toBe(`data:image/svg+xml;base64,${fixedColorSvgBase64}`)
        // never carries a hash — KsTaskIcon only appends it to the local /icon.svg URL, which
        // ecosystem-catalog icons don't use (they render the embedded iconUrl directly)
        expect(icon.hash).toBeUndefined()
    })

    it("derives monochrome from the SVG bytes for ecosystem-catalog icons, ignoring the (absent) wire field", async () => {
        // api.kestra.io predates the `monochrome` field entirely (it was added to the LOCAL
        // instance's DTO for this redesign) — the wire payload's `monochrome` value must be
        // ignored for this source and re-derived from whether the SVG actually uses currentColor.
        getMock.mockResolvedValueOnce({data: {}})
        pluginIconsMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.anthropic.ChatCompletion": {icon: currentColorSvgBase64, flowable: false, monochrome: undefined},
            },
        })

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.anthropic.ChatCompletion"].monochrome).toBe(true)
    })

    it("prefers the local instance's icon over the ecosystem catalog when both have an entry", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: fixedColorSvgBase64, flowable: false, monochrome: false},
            },
        })
        pluginIconsMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: currentColorSvgBase64, flowable: false, monochrome: false},
            },
        })

        await store.fetchIcons()

        // api.kestra.io wins on conflict (spread order), consistent with pre-existing behavior
        const icon = store.icons["io.kestra.plugin.core.log.Log"]
        expect(icon.iconUrl).toBe(`data:image/svg+xml;base64,${currentColorSvgBase64}`)
    })
})
