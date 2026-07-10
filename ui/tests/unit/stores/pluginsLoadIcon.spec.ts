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

vi.mock("../../../src/stores/api", () => ({
    API_URL: "https://api.kestra.io",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

let nextImageOutcome: "load" | "error" = "error"
let lastImageSrc: string | undefined

class FakeImage {
    onload: (() => void) | null = null
    onerror: (() => void) | null = null

    set src(url: string) {
        lastImageSrc = url
        const outcome = nextImageOutcome
        queueMicrotask(() => {
            if (outcome === "load") this.onload?.()
            else this.onerror?.()
        })
    }
}

describe("plugins store loadIcon", () => {
    let store: any

    beforeEach(async () => {
        getMock.mockReset()
        nextImageOutcome = "error"
        lastImageSrc = undefined
        vi.stubGlobal("Image", FakeImage)
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
        const raw = {icon: null, flowable: true, monochrome: false}
        getMock.mockResolvedValueOnce({data: {icon: raw}})

        const result = await store.loadIcon("io.kestra.plugin.core.debug.NoIcon")

        expect(result).toEqual({flowable: true, monochrome: false, hasIcon: false})
        expect(lastImageSrc).toBeUndefined()
    })

    it("falls back to the ecosystem catalog when the class isn't registered locally", async () => {
        getMock.mockResolvedValueOnce({data: {icon: null}})
        nextImageOutcome = "load"

        const result = await store.loadIcon("io.kestra.plugin.scripts.python.Commands")

        expect(result).toEqual({
            flowable: false,
            monochrome: false,
            hasIcon: true,
            iconUrl: "https://api.kestra.io/v1/plugins/icons/io.kestra.plugin.scripts.python.Commands",
        })
        expect(lastImageSrc).toBe("https://api.kestra.io/v1/plugins/icons/io.kestra.plugin.scripts.python.Commands")
    })

    it("never flags ecosystem icons as monochrome", async () => {
        getMock.mockResolvedValueOnce({data: {icon: null}})
        nextImageOutcome = "load"

        const result = await store.loadIcon("io.kestra.plugin.anthropic.ChatCompletion")

        expect(result?.monochrome).toBe(false)
    })

    it("resolves to undefined without throwing when neither the local instance nor the ecosystem catalog has the class", async () => {
        getMock.mockResolvedValueOnce({data: {icon: null}})
        nextImageOutcome = "error"

        const result = await store.loadIcon("io.kestra.plugin.unknown.Task")

        expect(result).toBeUndefined()
    })

    it("resolves to undefined without throwing when the local request itself fails", async () => {
        getMock.mockRejectedValueOnce(new Error("network error"))
        nextImageOutcome = "error"

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

        nextImageOutcome = "load"

        const result = await store.loadIcon("io.kestra.plugin.scripts.python.Commands")

        expect(getMock).toHaveBeenCalledTimes(1)
        expect(result?.hasIcon).toBe(true)
    })
})
