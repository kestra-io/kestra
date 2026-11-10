import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {flushPromises} from "@vue/test-utils"

const {publicFetchJson, countUniquePluginElements} = vi.hoisted(() => ({
    publicFetchJson: vi.fn(),
    countUniquePluginElements: vi.fn(),
}))

vi.mock("../utils/publicApi", () => ({publicFetchJson}))
vi.mock("../utils/pluginUtils", () => ({countUniquePluginElements}))
vi.mock("../stores/api", () => ({API_URL: "https://api.test"}))

const CACHE_KEY = "plugins.catalogCount"

// The composable caches its request and count at module scope, so every case loads a fresh copy.
async function loadComposable() {
    vi.resetModules()
    const {usePluginsCount} = await import("./usePluginsCount")
    return usePluginsCount
}

describe("usePluginsCount", () => {
    beforeEach(() => {
        publicFetchJson.mockReset()
        countUniquePluginElements.mockReset()
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.removeItem(CACHE_KEY)
    })

    it("has no count until the request resolves", async () => {
        publicFetchJson.mockReturnValue(new Promise(() => {}))
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        expect(totalPlugins.value).toBeUndefined()
    })

    it("rounds the fetched count down to the nearest hundred", async () => {
        publicFetchJson.mockResolvedValue([{}])
        countUniquePluginElements.mockReturnValue(1899)
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(totalPlugins.value).toBe(1800)
    })

    it("stays without a count when the rounded count is 0", async () => {
        publicFetchJson.mockResolvedValue([])
        countUniquePluginElements.mockReturnValue(99)
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(countUniquePluginElements).toHaveBeenCalled()
        expect(totalPlugins.value).toBeUndefined()
    })

    it("stays without a count and does not throw when the request fails", async () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
        publicFetchJson.mockRejectedValue(new Error("network down"))
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(warn).toHaveBeenCalled()
        expect(totalPlugins.value).toBeUndefined()
        warn.mockRestore()
    })

    it("issues a single request no matter how many times it is called", async () => {
        publicFetchJson.mockResolvedValue([])
        countUniquePluginElements.mockReturnValue(1800)
        const usePluginsCount = await loadComposable()

        usePluginsCount()
        usePluginsCount()

        expect(publicFetchJson).toHaveBeenCalledTimes(1)
    })

    it("sends the request with a 5 second timeout", async () => {
        const timeout = vi.spyOn(AbortSignal, "timeout")
        publicFetchJson.mockResolvedValue([])
        countUniquePluginElements.mockReturnValue(1800)
        const usePluginsCount = await loadComposable()

        usePluginsCount()

        expect(timeout).toHaveBeenCalledWith(5000)
    })

    it("reuses a count cached less than two hours ago without a request", async () => {
        localStorage.setItem(CACHE_KEY, JSON.stringify({count: 2400, fetchedAt: Date.now() - 60 * 60 * 1000}))
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        expect(totalPlugins.value).toBe(2400)
        expect(publicFetchJson).not.toHaveBeenCalled()
    })

    it("shows a stale cached count while fetching a fresh one", async () => {
        localStorage.setItem(CACHE_KEY, JSON.stringify({count: 2400, fetchedAt: Date.now() - 3 * 60 * 60 * 1000}))
        publicFetchJson.mockResolvedValue([{}])
        countUniquePluginElements.mockReturnValue(2512)
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()
        expect(totalPlugins.value).toBe(2400)

        await flushPromises()
        expect(totalPlugins.value).toBe(2500)
        expect(JSON.parse(localStorage.getItem(CACHE_KEY)!).count).toBe(2500)
    })
})
