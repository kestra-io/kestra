import {describe, expect, it, vi} from "vitest"

const {pluginUiManifest, loadRemote} = vi.hoisted(() => ({
    pluginUiManifest: vi.fn(),
    loadRemote: vi.fn(async (id: string) => ({default: {render: () => null}, ...(id.endsWith("model-graph") ? {getSubtitle: () => "8 nodes"} : {})})),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({pluginUiManifest}))
vi.mock("@module-federation/enhanced/runtime", () => ({loadRemote, registerRemotes: vi.fn(), registerShared: vi.fn()}))
vi.mock("override/utils/route", () => ({apiUrlWithoutTenants: () => "/api/v1"}))
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

import {useFederatedModule} from "./useFederatedModule"

describe("useFederatedModule", () => {
    it("should load every execution-artifact module of a task type under its own key", async () => {
        pluginUiManifest.mockResolvedValue({
            manifest: {
                "io.kestra.plugin.dbt.cli.DbtCLI": [
                    {uiModule: "execution-artifact/model-graph", group: "io.kestra.plugin.dbt", staticInfo: {title: "Model graph"}},
                    {uiModule: "execution-artifact/compiled-sql", group: "io.kestra.plugin.dbt", staticInfo: {title: "Compiled SQL"}},
                    {uiModule: "topology-details", group: "io.kestra.plugin.dbt", staticInfo: {height: 80}},
                ],
            },
        })
        const {resolveRemoteComponent, taskAdditionalInfoRemote, hasResolvedComponent, subtitleResolvers} = useFederatedModule("execution-artifact")

        await resolveRemoteComponent([{cls: "io.kestra.plugin.dbt.cli.DbtCLI", version: undefined}])

        expect(Object.keys(taskAdditionalInfoRemote.value)).toEqual([
            "io.kestra.plugin.dbt.cli.DbtCLI/model-graph",
            "io.kestra.plugin.dbt.cli.DbtCLI/compiled-sql",
        ])
        expect(hasResolvedComponent("io.kestra.plugin.dbt.cli.DbtCLI/compiled-sql")).toBe(true)
        expect(Object.keys(subtitleResolvers)).toEqual(["io.kestra.plugin.dbt.cli.DbtCLI/model-graph"])
        expect(loadRemote).toHaveBeenCalledWith("remote--io.kestra.plugin.dbt.cli.DbtCLI/cli.DbtCLI/execution-artifact/model-graph")
    })
})
