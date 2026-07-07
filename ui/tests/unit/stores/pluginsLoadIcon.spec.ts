import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const pluginIconMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginIcon: pluginIconMock,
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

describe("plugins store loadIcon", () => {
    let store: any

    beforeEach(async () => {
        pluginIconMock.mockReset()
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
    })

    it("resolves the icon and caches it when the backend finds one", async () => {
        const icon = {icon: "base64svg", flowable: false}
        pluginIconMock.mockResolvedValueOnce({icon})

        const result = await store.loadIcon("io.kestra.plugin.core.log.Log")

        expect(result).toEqual(icon)
        expect(pluginIconMock).toHaveBeenCalledTimes(1)

        const cached = await store.loadIcon("io.kestra.plugin.core.log.Log")
        expect(cached).toEqual(icon)
        expect(pluginIconMock).toHaveBeenCalledTimes(1)
    })

    it("resolves to undefined without throwing when the backend answers 200 with a null icon", async () => {
        pluginIconMock.mockResolvedValueOnce({icon: null})

        const result = await store.loadIcon("io.kestra.plugin.unknown.Task")

        expect(result).toBeUndefined()
    })

    it("resolves to undefined without throwing when the request itself fails", async () => {
        pluginIconMock.mockRejectedValueOnce(new Error("network error"))

        const result = await store.loadIcon("io.kestra.plugin.unknown.Task")

        expect(result).toBeUndefined()
    })

    it("dedupes concurrent requests for the same class", async () => {
        let resolveRequest: (value: any) => void = () => {}
        pluginIconMock.mockReturnValueOnce(new Promise(resolve => {
            resolveRequest = resolve
        }))

        const first = store.loadIcon("io.kestra.plugin.core.log.Log")
        const second = store.loadIcon("io.kestra.plugin.core.log.Log")

        expect(pluginIconMock).toHaveBeenCalledTimes(1)

        resolveRequest({icon: {icon: "base64svg", flowable: false}})

        const [firstResult, secondResult] = await Promise.all([first, second])
        expect(firstResult).toEqual(secondResult)
    })
})
