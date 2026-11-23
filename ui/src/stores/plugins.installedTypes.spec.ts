import {describe, it, expect, vi, beforeEach} from "vitest"
import type {MockedFunction} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import type {usePluginsStore} from "./plugins"
import type {listPlugins as listPluginsFn} from "@kestra-io/kestra-sdk/plugins"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    listPlugins: vi.fn(),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

describe("plugins store installed types", () => {
    let store: ReturnType<typeof usePluginsStore>
    let listPlugins: MockedFunction<typeof listPluginsFn>

    beforeEach(async () => {
        vi.clearAllMocks()
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("./plugins")
        store = usePluginsStore()
        listPlugins = vi.mocked((await import("@kestra-io/kestra-sdk/plugins")).listPlugins)
    })

    it("includes element classes and aliases so renamed task types count as installed", async () => {
        listPlugins.mockResolvedValue({
            results: [
                {
                    name: "docker",
                    group: "io.kestra.plugin.docker",
                    title: "Docker",
                    tasks: [{cls: "io.kestra.plugin.docker.Run"}],
                    aliases: ["io.kestra.plugin.scripts.docker.Run"],
                },
                {
                    name: "core",
                    group: "io.kestra.plugin.core",
                    title: "Core",
                    tasks: [{cls: "io.kestra.plugin.core.log.Log"}],
                },
            ],
            total: 2,
        })

        const types = await store.loadInstalledPluginTypes()

        expect(types).toContain("io.kestra.plugin.docker.Run")
        expect(types).toContain("io.kestra.plugin.scripts.docker.Run")
        expect(types).toContain("io.kestra.plugin.core.log.Log")
    })

    it("loads once and reuses the cached list", async () => {
        listPlugins.mockResolvedValue({results: [], total: 0})
        await store.loadInstalledPluginTypes()
        await store.loadInstalledPluginTypes()
        expect(listPlugins).toHaveBeenCalledOnce()
    })
})
