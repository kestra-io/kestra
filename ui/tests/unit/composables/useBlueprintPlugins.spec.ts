import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

const SUBGROUP_BQ = {
    name: "gcp",
    group: "io.kestra.plugin.gcp",
    subGroup: "io.kestra.plugin.gcp.bigquery",
    title: "BigQuery",
    tasks: [
        {cls: "io.kestra.plugin.gcp.bigquery.Query", deprecated: false},
        {cls: "io.kestra.plugin.gcp.bigquery.Load", deprecated: false},
    ],
}
const STANDALONE = {
    name: "subflow",
    group: "io.kestra.plugin.core",
    title: "Core",
    tasks: [{cls: "io.kestra.plugin.core.flow.Subflow", deprecated: false}],
}

describe("useBlueprintPlugins", () => {
    let pluginsStore: any
    let composable: any

    beforeEach(async () => {
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        pluginsStore = usePluginsStore()
        pluginsStore.plugins = [SUBGROUP_BQ, STANDALONE]

        const {useBlueprintPlugins} = await import("../../../src/composables/useBlueprintPlugins")
        composable = useBlueprintPlugins()
    })

    describe("missingTaskTypes", () => {
        it("returns task types whose plugin is not installed", () => {
            expect(
                composable.missingTaskTypes([
                    "io.kestra.plugin.gcp.bigquery.Query",
                    "io.kestra.plugin.aws.s3.Upload",
                ]),
            ).toEqual(["io.kestra.plugin.aws.s3.Upload"])
        })

        it("returns an empty array when every task is installed", () => {
            expect(
                composable.missingTaskTypes(["io.kestra.plugin.core.flow.Subflow"]),
            ).toEqual([])
        })

        it("deduplicates the included task types", () => {
            expect(
                composable.missingTaskTypes([
                    "io.kestra.plugin.aws.s3.Upload",
                    "io.kestra.plugin.aws.s3.Upload",
                ]),
            ).toEqual(["io.kestra.plugin.aws.s3.Upload"])
        })

        it("handles missing/empty includedTasks", () => {
            expect(composable.missingTaskTypes(undefined)).toEqual([])
            expect(composable.missingTaskTypes([])).toEqual([])
        })
    })

    describe("missingPluginNames", () => {
        it("derives unique, sorted plugin names from the missing task types", () => {
            expect(
                composable.missingPluginNames([
                    "io.kestra.plugin.gcp.bigquery.Query",
                    "io.kestra.plugin.dbt.cloud.Trigger",
                    "io.kestra.plugin.aws.s3.Upload",
                    "io.kestra.plugin.aws.s3.Download",
                ]),
            ).toEqual(["aws", "dbt"])
        })
    })

    describe("pluginName", () => {
        it("extracts the plugin segment from a standard plugin class", () => {
            expect(composable.pluginName("io.kestra.plugin.gcp.bigquery.Query")).toBe("gcp")
        })

        it("falls back to the enclosing package for non-standard classes", () => {
            expect(composable.pluginName("com.acme.tasks.DoStuff")).toBe("tasks")
        })
    })

    describe("hasMissingPlugins", () => {
        it("is true when at least one plugin is missing", () => {
            expect(composable.hasMissingPlugins(["io.kestra.plugin.aws.s3.Upload"])).toBe(true)
        })

        it("is false when all plugins are installed", () => {
            expect(composable.hasMissingPlugins(["io.kestra.plugin.gcp.bigquery.Load"])).toBe(false)
        })
    })

    describe("ensureInstalledPluginsLoaded", () => {
        it("does not refetch when task types are already loaded", async () => {
            const spy = vi.spyOn(pluginsStore, "list")
            await composable.ensureInstalledPluginsLoaded()
            expect(spy).not.toHaveBeenCalled()
        })

        it("fetches the plugin list when nothing is loaded yet", async () => {
            pluginsStore.plugins = []
            const spy = vi.spyOn(pluginsStore, "list").mockResolvedValue([])
            await composable.ensureInstalledPluginsLoaded()
            expect(spy).toHaveBeenCalledOnce()
        })
    })
})
