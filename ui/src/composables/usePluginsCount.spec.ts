import {describe, it, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"

const {get, countUniquePluginElements} = vi.hoisted(() => ({
    get: vi.fn(),
    countUniquePluginElements: vi.fn(),
}))

vi.mock("axios", () => ({default: {get}}))
vi.mock("../utils/pluginUtils", () => ({countUniquePluginElements}))
vi.mock("../utils/pluginCatalogCount", () => ({PLUGIN_CATALOG_COUNT: 1234}))
vi.mock("../stores/api", () => ({API_URL: "https://api.test"}))

// The composable caches its request and count at module scope, so every case loads a fresh copy.
async function loadComposable() {
    vi.resetModules()
    const {usePluginsCount} = await import("./usePluginsCount")
    return usePluginsCount
}

describe("usePluginsCount", () => {
    beforeEach(() => {
        get.mockReset()
        countUniquePluginElements.mockReset()
    })

    it("starts at the catalog baseline until the request resolves", async () => {
        get.mockReturnValue(new Promise(() => {}))
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        expect(totalPlugins.value).toBe(1234)
    })

    it("rounds the fetched count down to the nearest hundred", async () => {
        get.mockResolvedValue({data: [{}]})
        countUniquePluginElements.mockReturnValue(1899)
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(totalPlugins.value).toBe(1800)
    })

    it("keeps the baseline when the rounded count is 0", async () => {
        get.mockResolvedValue({data: []})
        countUniquePluginElements.mockReturnValue(99)
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(countUniquePluginElements).toHaveBeenCalled()
        expect(totalPlugins.value).toBe(1234)
    })

    it("keeps the baseline and does not throw when the request fails", async () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
        get.mockRejectedValue(new Error("network down"))
        const usePluginsCount = await loadComposable()

        const {totalPlugins} = usePluginsCount()

        await flushPromises()
        expect(warn).toHaveBeenCalled()
        expect(totalPlugins.value).toBe(1234)
        warn.mockRestore()
    })

    it("issues a single request no matter how many times it is called", async () => {
        get.mockResolvedValue({data: []})
        countUniquePluginElements.mockReturnValue(1800)
        const usePluginsCount = await loadComposable()

        usePluginsCount()
        usePluginsCount()

        expect(get).toHaveBeenCalledTimes(1)
    })

    it("sends the request with a 5 second timeout", async () => {
        get.mockResolvedValue({data: []})
        countUniquePluginElements.mockReturnValue(1800)
        const usePluginsCount = await loadComposable()

        usePluginsCount()

        expect(get).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({timeout: 5000}))
    })
})
