import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const getMock = vi.fn()
const pluginIconMock = vi.fn()

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
    useApiStore: () => ({pluginIcon: pluginIconMock}),
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

describe("plugins store loadIcon", () => {
    let store: any

    beforeEach(async () => {
        getMock.mockReset()
        pluginIconMock.mockReset()
        // Default: the ecosystem catalog doesn't have the class either, unless a test overrides it.
        pluginIconMock.mockRejectedValue(new Error("not found"))
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
    })

    it("resolves the icon and caches it when the backend finds one", async () => {
        const raw = {icon: "base64svg", flowable: false, monochrome: false}
        getMock.mockResolvedValueOnce({data: {icon: raw}})

        const result = await store.loadIcon("io.kestra.plugin.core.log.Log")

        expect(result).toEqual({flowable: false, monochrome: false, hasIcon: true})
        expect(getMock).toHaveBeenCalledTimes(1)

        const cached = await store.loadIcon("io.kestra.plugin.core.log.Log")
        expect(cached).toEqual({flowable: false, monochrome: false, hasIcon: true})
        expect(getMock).toHaveBeenCalledTimes(1)
    })

    it("passes the monochrome field through untouched", async () => {
        const raw = {icon: "base64svg", flowable: false, monochrome: true}
        getMock.mockResolvedValueOnce({data: {icon: raw}})

        const result = await store.loadIcon("io.kestra.plugin.core.debug.Echo")

        expect(result?.monochrome).toBe(true)
    })

    it("derives hasIcon: false for a registered class that ships no icon file", async () => {
        // The backend still returns an entry (with a real `flowable`) for every registered
        // task/trigger class even when it has no icon — only the nested `icon` field is null.
        const raw = {icon: null, flowable: true, monochrome: false}
        getMock.mockResolvedValueOnce({data: {icon: raw}})

        const result = await store.loadIcon("io.kestra.plugin.core.debug.NoIcon")

        expect(result).toEqual({flowable: true, monochrome: false, hasIcon: false})
        // Registered-but-iconless is a legitimate, already-known outcome — no need to fall back
        // to the ecosystem catalog for it.
        expect(pluginIconMock).not.toHaveBeenCalled()
    })

    it("falls back to the ecosystem catalog when the class isn't registered locally", async () => {
        // Top-level `icon: null` means the class isn't registered at all (distinct from a
        // registered-but-iconless class, which nests `icon: null` one level deeper).
        getMock.mockResolvedValueOnce({data: {icon: null}})
        pluginIconMock.mockResolvedValueOnce({data: "<svg fill=\"blue\"></svg>"})

        const result = await store.loadIcon("io.kestra.plugin.scripts.python.Commands")

        expect(result).toEqual({
            flowable: false,
            monochrome: false,
            hasIcon: true,
            iconUrl: "https://api.kestra.io/v1/plugins/icons/io.kestra.plugin.scripts.python.Commands",
        })
    })

    it("derives monochrome from the ecosystem SVG bytes", async () => {
        getMock.mockResolvedValueOnce({data: {icon: null}})
        pluginIconMock.mockResolvedValueOnce({data: "<svg fill=\"currentColor\"></svg>"})

        const result = await store.loadIcon("io.kestra.plugin.anthropic.ChatCompletion")

        expect(result?.monochrome).toBe(true)
    })

    it("resolves to undefined without throwing when neither the local instance nor the ecosystem catalog has the class", async () => {
        getMock.mockResolvedValueOnce({data: {icon: null}})

        const result = await store.loadIcon("io.kestra.plugin.unknown.Task")

        expect(result).toBeUndefined()
    })

    it("resolves to undefined without throwing when the local request itself fails", async () => {
        getMock.mockRejectedValueOnce(new Error("network error"))

        const result = await store.loadIcon("io.kestra.plugin.unknown.Task")

        expect(result).toBeUndefined()
    })

    it("dedupes concurrent requests for the same class", async () => {
        let resolveRequest: (value: any) => void = () => {}
        getMock.mockReturnValueOnce(new Promise(resolve => {
            resolveRequest = resolve
        }))

        const first = store.loadIcon("io.kestra.plugin.core.log.Log")
        const second = store.loadIcon("io.kestra.plugin.core.log.Log")

        expect(getMock).toHaveBeenCalledTimes(1)

        resolveRequest({data: {icon: {icon: "base64svg", flowable: false, monochrome: false}}})

        const [firstResult, secondResult] = await Promise.all([first, second])
        expect(firstResult).toEqual(secondResult)
    })

    it("skips the local per-class lookup and goes straight to the ecosystem catalog once the full local catalog is loaded", async () => {
        getMock.mockResolvedValueOnce({data: {}})
        await store.fetchIcons()

        pluginIconMock.mockResolvedValueOnce({data: "<svg fill=\"blue\"></svg>"})

        const result = await store.loadIcon("io.kestra.plugin.scripts.python.Commands")

        expect(getMock).toHaveBeenCalledTimes(1)
        expect(result?.hasIcon).toBe(true)
    })
})
