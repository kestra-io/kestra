import {describe, it, expect} from "vitest"
import {countUniquePluginElements, getPluginReleaseUrl, isEnterpriseEditionPlugin, type Plugin} from "../../../src/utils/pluginUtils"

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
