import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

const getMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: getMock, post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

describe("plugins store fetchIcons", () => {
    let store: any

    beforeEach(async () => {
        getMock.mockReset()
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        store = usePluginsStore()
    })

    it("does not set iconUrl for icons resolved from the local instance", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: "base64svg", flowable: false, monochrome: false},
            },
        })

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
                "io.kestra.plugin.core.log.Log": {icon: "base64svg", flowable: false, monochrome: false, hash: "abc123"},
            },
        })

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.core.log.Log"].hash).toBe("abc123")
    })

    it("derives hasIcon: false for a registered class that ships no icon file", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.debug.NoIcon": {icon: null, flowable: true, monochrome: false},
            },
        })

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.core.debug.NoIcon"]).toEqual({
            flowable: true,
            monochrome: false,
            hasIcon: false,
        })
    })

    it("derives hasIcon from the hash when the index carries no icon bytes", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.core.log.Log": {icon: null, flowable: false, monochrome: false, hash: "abc123"},
            },
        })

        await store.fetchIcons()

        expect(store.icons["io.kestra.plugin.core.log.Log"]).toEqual({
            flowable: false,
            monochrome: false,
            hasIcon: true,
            hash: "abc123",
        })
    })

    it("drops iconless groups so they keep falling back to the ecosystem catalog", async () => {
        getMock.mockResolvedValueOnce({
            data: {
                "io.kestra.plugin.aws": {icon: null, flowable: false, monochrome: false, hash: "abc123"},
                "io.kestra.storage.seaweedfs": {icon: null, flowable: false, monochrome: false},
            },
        })

        await store.ensureGroupIcons()

        expect(store.groupIcons["io.kestra.plugin.aws"].hasIcon).toBe(true)
        expect(store.groupIcons["io.kestra.storage.seaweedfs"]).toBeUndefined()
    })

    it("only fetches the local catalog once and caches the result", async () => {
        getMock.mockResolvedValueOnce({data: {}})

        await store.fetchIcons()
        await store.fetchIcons()

        expect(getMock).toHaveBeenCalledTimes(1)
    })
})
