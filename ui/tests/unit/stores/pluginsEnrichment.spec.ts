import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import axios from "axios"

vi.mock("axios")
vi.mock("../../../src/stores/api", () => ({API_URL: "https://api.test"}))

const mockedGet = vi.mocked(axios.get)

describe("pluginsEnrichment store — fetchVersions cache", () => {
    let store: any

    beforeEach(async () => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
        const {usePluginsEnrichmentStore} = await import("../../../src/stores/pluginsEnrichment")
        store = usePluginsEnrichmentStore()
    })

    it("caches a successful response and does not refetch", async () => {
        const data = [{version: "1.0.0"}, {version: "0.9.0"}]
        mockedGet.mockResolvedValue({data} as any)

        const first = await store.fetchVersions("io.kestra.plugin.x.Y")
        const second = await store.fetchVersions("io.kestra.plugin.x.Y")

        expect(first).toEqual(data)
        expect(second).toEqual(data)
        expect(mockedGet).toHaveBeenCalledTimes(1)
        expect(store.getVersions("io.kestra.plugin.x.Y")).toEqual(data)
    })

    it("dedupes concurrent in-flight requests for the same cls", async () => {
        mockedGet.mockResolvedValue({data: [{version: "1.0.0"}]} as any)

        const [a, b] = await Promise.all([
            store.fetchVersions("io.kestra.plugin.x.Y"),
            store.fetchVersions("io.kestra.plugin.x.Y"),
        ])

        expect(a).toEqual(b)
        expect(mockedGet).toHaveBeenCalledTimes(1)
    })

    it("does NOT cache a failure, so a later call retries", async () => {
        // The store intentionally console.warns on a failed fetch — silence the
        // expected warning this test deliberately triggers.
        const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {})
        mockedGet.mockRejectedValueOnce(new Error("network"))

        const failed = await store.fetchVersions("io.kestra.plugin.x.Y")
        expect(failed).toEqual([])
        expect(store.getVersions("io.kestra.plugin.x.Y")).toEqual([])

        const data = [{version: "1.0.0"}]
        mockedGet.mockResolvedValueOnce({data} as any)
        const retried = await store.fetchVersions("io.kestra.plugin.x.Y")

        expect(retried).toEqual(data)
        expect(mockedGet).toHaveBeenCalledTimes(2)
        expect(warnSpy).toHaveBeenCalledWith("Failed to load plugin versions", "io.kestra.plugin.x.Y", expect.any(Error))
        warnSpy.mockRestore()
    })
})

describe("pluginsEnrichment store — fetchEnrichment titles", () => {
    let store: any

    beforeEach(async () => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
        const {usePluginsEnrichmentStore} = await import("../../../src/stores/pluginsEnrichment")
        store = usePluginsEnrichmentStore()
    })

    it("enriches display titles from the public subgroups catalog", async () => {
        mockedGet.mockImplementation(((url: string) => {
            if (url.endsWith("/v1/plugins/subgroups")) {
                return Promise.resolve({data: [
                    {group: "io.kestra.plugin.core", subGroup: "io.kestra.plugin.core.debug", title: "Debug"},
                    {group: "io.kestra.plugin.core", subGroup: null, title: "Core Plugins and tasks"},
                    {group: "io.kestra.plugin.x", subGroup: "io.kestra.plugin.x.y"},
                ]})
            }
            return Promise.reject(new Error("unavailable"))
        }) as any)

        await store.fetchEnrichment()

        expect(store.getEnrichment({group: "io.kestra.plugin.core", subGroup: "io.kestra.plugin.core.debug"})?.title).toBe("Debug")
        expect(store.getEnrichment({group: "io.kestra.plugin.core"})?.title).toBe("Core Plugins and tasks")
        expect(store.getEnrichment({group: "io.kestra.plugin.x", subGroup: "io.kestra.plugin.x.y"})).toBeNull()
    })
})
