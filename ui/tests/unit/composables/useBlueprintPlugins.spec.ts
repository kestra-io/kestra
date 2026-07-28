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

// Exact task/trigger/runner classes the plugins endpoint returns. Note it never
// returns conditions, and sibling artifacts under the same package (scripts.*)
// are installed independently — scripts.shell is present here, scripts.python is not.
const INSTALLED_TYPES = [
    "io.kestra.plugin.core.log.Log",
    "io.kestra.plugin.core.trigger.Flow",
    "io.kestra.plugin.core.runner.Process",
    "io.kestra.plugin.scripts.shell.Commands",
    "io.kestra.plugin.gcp.bigquery.Query",
]

describe("useBlueprintPlugins", () => {
    let pluginsStore: any
    let composable: any

    beforeEach(async () => {
        setActivePinia(createPinia())
        const {usePluginsStore} = await import("../../../src/stores/plugins")
        pluginsStore = usePluginsStore()
        pluginsStore.installedPluginTypes = [...INSTALLED_TYPES]

        const {useBlueprintPlugins} = await import("../../../src/composables/useBlueprintPlugins")
        composable = useBlueprintPlugins()
    })

    describe("missingTaskTypes", () => {
        it("flags a task whose exact class is not installed", () => {
            expect(
                composable.missingTaskTypes([
                    "io.kestra.plugin.gcp.bigquery.Query",
                    "io.kestra.plugin.scripts.python.Commands",
                ]),
            ).toEqual(["io.kestra.plugin.scripts.python.Commands"])
        })

        it("distinguishes sibling artifacts sharing a package prefix", () => {
            // scripts.shell is installed, scripts.python is not — group matching
            // would wrongly treat both as present.
            expect(
                composable.missingTaskTypes([
                    "io.kestra.plugin.scripts.shell.Commands",
                    "io.kestra.plugin.scripts.python.Commands",
                ]),
            ).toEqual(["io.kestra.plugin.scripts.python.Commands"])
        })

        it("never flags condition classes (absent from the plugins endpoint)", () => {
            expect(
                composable.missingTaskTypes([
                    "io.kestra.plugin.core.log.Log",
                    "io.kestra.plugin.core.trigger.Flow",
                    "io.kestra.plugin.core.condition.Not",
                    "io.kestra.plugin.core.condition.ExecutionNamespace",
                    "io.kestra.core.models.conditions.types.MultipleCondition",
                ]),
            ).toEqual([])
        })

        it("returns nothing while the installed set is unknown (never disables blindly)", () => {
            pluginsStore.installedPluginTypes = undefined
            expect(
                composable.missingTaskTypes(["io.kestra.plugin.scripts.python.Commands"]),
            ).toEqual([])
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
                    "io.kestra.plugin.dbt.cloud.Trigger",
                    "io.kestra.plugin.scripts.python.Commands",
                    "io.kestra.plugin.aws.s3.Upload",
                ]),
            ).toEqual(["aws", "dbt", "scripts"])
        })
    })

    describe("hasMissingPlugins", () => {
        it("is true when at least one task's plugin is missing", () => {
            expect(composable.hasMissingPlugins(["io.kestra.plugin.scripts.python.Commands"])).toBe(true)
        })

        it("is false when every task is installed", () => {
            expect(composable.hasMissingPlugins(["io.kestra.plugin.gcp.bigquery.Query"])).toBe(false)
        })
    })

    describe("ensureInstalledPluginsLoaded", () => {
        it("delegates to the store loader", async () => {
            const spy = vi.spyOn(pluginsStore, "loadInstalledPluginTypes").mockResolvedValue([])
            await composable.ensureInstalledPluginsLoaded()
            expect(spy).toHaveBeenCalledOnce()
        })

        it("swallows loader errors", async () => {
            vi.spyOn(pluginsStore, "loadInstalledPluginTypes").mockRejectedValue(new Error("boom"))
            await expect(composable.ensureInstalledPluginsLoaded()).resolves.toBeUndefined()
        })
    })
})
