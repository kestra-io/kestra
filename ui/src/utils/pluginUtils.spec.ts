import {describe, it, expect} from "vitest"
import {countUniquePluginElements, extractPluginElements, getPluginReleaseUrl, isEnterpriseEditionPlugin, isEntryAPluginElementPredicate, isPluginMatched, type Plugin} from "./pluginUtils"

describe("getPluginReleaseUrl", () => {
    it("returns the Kestra core repo for core plugins", () => {
        expect(getPluginReleaseUrl("io.kestra.plugin.core.debug.Return"))
            .toBe("https://github.com/kestra-io/kestra/releases")
    })

    it("returns the plugin-<name> repo for a standard plugin", () => {
        expect(getPluginReleaseUrl("io.kestra.plugin.azure.storage.blob.Download"))
            .toBe("https://github.com/kestra-io/plugin-azure/releases")
    })

    it("uses the storage- prefix for storage plugins", () => {
        expect(getPluginReleaseUrl("io.kestra.storage.s3.Upload"))
            .toBe("https://github.com/kestra-io/storage-s3/releases")
    })

    it("returns null for Enterprise (ee) plugins", () => {
        expect(getPluginReleaseUrl("io.kestra.plugin.ee.azure.Foo")).toBeNull()
    })

    it("returns null for secret plugins", () => {
        expect(getPluginReleaseUrl("io.kestra.plugin.secret.Foo")).toBeNull()
    })

    it("returns null for missing or malformed input", () => {
        expect(getPluginReleaseUrl(undefined)).toBeNull()
        expect(getPluginReleaseUrl("io.kestra")).toBeNull()
    })
})

describe("countUniquePluginElements", () => {
    const plugin = (group: string, elements: Partial<Plugin>): Plugin => ({
        name: group,
        title: group,
        group,
        ...elements,
    })

    it("counts element classes across plugins and element types", () => {
        expect(countUniquePluginElements([
            plugin("io.kestra.plugin.a", {
                tasks: [{cls: "io.kestra.plugin.a.Run"}, {cls: "io.kestra.plugin.a.Query"}],
                triggers: [{cls: "io.kestra.plugin.a.Watch"}],
            }),
            plugin("io.kestra.plugin.b", {
                tasks: [{cls: "io.kestra.plugin.b.Run"}],
            }),
        ])).toBe(4)
    })

    it("deduplicates classes repeated across plugins", () => {
        expect(countUniquePluginElements([
            plugin("io.kestra.plugin.a", {tasks: [{cls: "io.kestra.plugin.a.Run"}]}),
            plugin("io.kestra.plugin.a.sub", {tasks: [{cls: "io.kestra.plugin.a.Run"}]}),
        ])).toBe(1)
    })

    it("ignores non-element arrays such as categories and aliases", () => {
        expect(countUniquePluginElements([
            plugin("io.kestra.plugin.a", {
                categories: ["CLOUD"],
                aliases: ["io.kestra.plugin.legacy.Run"],
                tasks: [{cls: "io.kestra.plugin.a.Run"}],
            }),
        ])).toBe(1)
    })

    it("returns 0 for an empty catalog", () => {
        expect(countUniquePluginElements([])).toBe(0)
        expect(countUniquePluginElements([plugin("io.kestra.plugin.a", {tasks: []})])).toBe(0)
    })
})

describe("isEnterpriseEditionPlugin", () => {
    it("detects the .ee. namespace segment", () => {
        expect(isEnterpriseEditionPlugin("io.kestra.plugin.ee.azure.runner.Batch")).toBe(true)
        expect(isEnterpriseEditionPlugin("io.kestra.plugin.ee.azure")).toBe(true)
    })

    it("returns false for OSS plugins", () => {
        expect(isEnterpriseEditionPlugin("io.kestra.plugin.azure.storage.blob.Download")).toBe(false)
    })

    it("returns false for null/undefined", () => {
        expect(isEnterpriseEditionPlugin(undefined)).toBe(false)
        expect(isEnterpriseEditionPlugin(null)).toBe(false)
    })
})

describe("extractPluginElements", () => {
    const plugin = (elements: Partial<Plugin>): Plugin => ({name: "io.kestra.plugin.a", title: "io.kestra.plugin.a", group: "io.kestra.plugin.a", ...elements})
    it("collects every element across a plugin's sections, skipping deprecated ones", () => {
        expect(extractPluginElements(plugin({
            tasks: [{cls: "io.kestra.plugin.a.Run"}, {cls: "io.kestra.plugin.a.Old", deprecated: true}],
            triggers: [{cls: "io.kestra.plugin.a.Watch"}],
        }))).toEqual({tasks: ["io.kestra.plugin.a.Run"], triggers: ["io.kestra.plugin.a.Watch"]})
    })
    it("splits camelCase section keys into words", () => {
        expect(extractPluginElements(plugin({taskRunners: [{cls: "io.kestra.plugin.a.Runner"}]}))).toEqual({"task Runners": ["io.kestra.plugin.a.Runner"]})
    })
    it("returns an empty object for a plugin with no elements", () => {
        expect(extractPluginElements(plugin({}))).toEqual({})
        expect(extractPluginElements(plugin({categories: ["CLOUD"]}))).toEqual({})
    })
})

describe("isPluginMatched", () => {
    const plugin = (elements: Partial<Plugin>): Plugin => ({name: "io.kestra.plugin.azure", title: "Azure", group: "io.kestra.plugin.azure", ...elements})
    it("matches on the plugin name, case-insensitively", () => {
        expect(isPluginMatched({name: "io.kestra.plugin.a", title: "Azure Storage", group: "io.kestra.plugin.a"}, "STORAGE")).toBe(true)
    })
    it("matches on an element's class name", () => {
        expect(isPluginMatched(plugin({tasks: [{cls: "io.kestra.plugin.azure.storage.blob.Download"}]}), "blob.download")).toBe(true)
    })
    it("is false for a term that appears nowhere", () => {
        expect(isPluginMatched(plugin({tasks: [{cls: "io.kestra.plugin.azure.storage.blob.Download"}]}), "snowflake")).toBe(false)
    })
})

describe("isEntryAPluginElementPredicate", () => {
    it("accepts a real element entry and rejects metadata keys", () => {
        expect(isEntryAPluginElementPredicate("tasks", [{cls: "io.kestra.plugin.a.Run"}])).toBe(true)
        expect(isEntryAPluginElementPredicate("categories", ["CLOUD"])).toBe(false)
        expect(isEntryAPluginElementPredicate("aliases", [{cls: "io.kestra.plugin.a.Run"}])).toBe(false)
        expect(isEntryAPluginElementPredicate("tasks", [{title: "Run"}])).toBe(false)
    })
})
