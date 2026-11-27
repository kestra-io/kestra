import {describe, test, expect, beforeEach, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {designSystemI18nReady} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import PluginUnified from "./PluginUnified.vue"

vi.mock("../../utils/publicApi", () => ({
    publicFetchJson: vi.fn().mockResolvedValue([]),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginBySubgroups: vi.fn().mockResolvedValue([
        {
            name: "plugin-dbt",
            title: "DBT",
            description: "Integrate dbt data transformations into Kestra orchestration pipelines.",
            longDescription: "# How to use the dbt plugin\n\n## Authentication\n\nUse `profiles`.",
            group: "io.kestra.plugin.dbt",
            tasks: [
                {cls: "io.kestra.plugin.dbt.cli.DbtCLI"},
                {cls: "io.kestra.plugin.dbt.cloud.CheckStatus"},
            ],
        },
        {
            name: "plugin-dbt",
            title: "dbt CLI",
            group: "io.kestra.plugin.dbt",
            subGroup: "io.kestra.plugin.dbt.cli",
            tasks: [{cls: "io.kestra.plugin.dbt.cli.DbtCLI"}],
        },
    ]),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({theme: "light", loadConfigs: vi.fn()}),
}))

async function mountUnified(props: {group: string, subgroup?: string}) {
    const wrapper = i18nMount(PluginUnified, {
        props,
        global: {stubs: {TaskIcon: true}},
    })
    await flushPromises()
    await designSystemI18nReady()
    return wrapper
}

describe("PluginUnified", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    test("renders the short description outside the markdown body", async () => {
        const wrapper = await mountUnified({group: "io.kestra.plugin.dbt"})

        expect(wrapper.find(".plugin-unified__summary").text())
            .toBe("Integrate dbt data transformations into Kestra orchestration pipelines.")
    })
})
