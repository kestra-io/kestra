import {describe, test, expect, beforeEach, afterEach, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import PluginGroup from "../../../../src/components/plugins/PluginGroup.vue"

vi.mock("axios", () => ({
    default: {get: vi.fn().mockResolvedValue({data: []})},
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn()}),
}))

// Same captured shape as PluginCatalog.spec.ts (kestra-io/kestra#17888): python's only subgroup
// is the bundled legacy package whose single task is deprecated, dbt has two visible subgroups.
vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginBySubgroups: vi.fn().mockResolvedValue([
        {
            name: "plugin-script-python",
            title: "Python",
            group: "io.kestra.plugin.scripts.python",
            tasks: [
                {cls: "io.kestra.core.tasks.scripts.Python", deprecated: true},
                {cls: "io.kestra.plugin.scripts.python.Commands"},
                {cls: "io.kestra.plugin.scripts.python.Script"},
            ],
            triggers: [
                {cls: "io.kestra.plugin.scripts.python.CommandsTrigger"},
                {cls: "io.kestra.plugin.scripts.python.ScriptTrigger"},
            ],
        },
        {
            name: "plugin-script-python",
            title: "scripts",
            group: "io.kestra.plugin.scripts.python",
            subGroup: "io.kestra.core.tasks.scripts",
            tasks: [{cls: "io.kestra.core.tasks.scripts.Python", deprecated: true}],
        },
        {
            name: "plugin-dbt",
            title: "DBT",
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
        {
            name: "plugin-dbt",
            title: "dbt Cloud",
            group: "io.kestra.plugin.dbt",
            subGroup: "io.kestra.plugin.dbt.cloud",
            tasks: [{cls: "io.kestra.plugin.dbt.cloud.CheckStatus"}],
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

const routeParams = vi.hoisted(() => ({} as Record<string, string>))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: routeParams, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

const enrichmentTitles = vi.hoisted(() => ({} as Record<string, string>))

vi.mock("../../../../src/stores/pluginsEnrichment", () => ({
    usePluginsEnrichmentStore: () => ({
        fetchEnrichment: vi.fn(),
        getEnrichment: (plugin: {subGroup?: string, group?: string} | undefined) => {
            const title = enrichmentTitles[plugin?.subGroup ?? plugin?.group ?? ""]
            return title ? {title} : null
        },
    }),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {en: {}},
})

async function mountGroup(name: string) {
    Object.keys(routeParams).forEach(key => delete routeParams[key])
    routeParams.name = name
    const wrapper = mount(PluginGroup, {
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {
                PluginLayout: {template: "<div><slot /></div>"},
                TaskIcon: true,
            },
        },
    })
    await flushPromises()
    return wrapper
}

describe("PluginGroup", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        Object.keys(enrichmentTitles).forEach(key => delete enrichmentTitles[key])
    })

    afterEach(() => {
        document.title = ""
    })

    test("lists tasks instead of a subgroup card when the only subgroup holds deprecated tasks", async () => {
        const wrapper = await mountGroup("plugin-script-python")
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).not.toContain("scripts")
        expect(titles).toContain("Script")
        expect(titles).toContain("Commands")
        expect(titles).toContain("ScriptTrigger")
        expect(titles).toContain("CommandsTrigger")
        expect(titles).not.toContain("Python")
    })

    test("prefers the display title from the public catalog when available", async () => {
        enrichmentTitles["io.kestra.plugin.dbt.cli"] = "dbt CLI from catalog"
        const wrapper = await mountGroup("plugin-dbt")
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).toContain("dbt CLI from catalog")
        expect(titles).not.toContain("dbt CLI")
    })

    test("lists subgroup cards when the subgroups have visible tasks", async () => {
        const wrapper = await mountGroup("plugin-dbt")
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).toContain("dbt CLI")
        expect(titles).toContain("dbt Cloud")
        expect(titles).not.toContain("DbtCLI")
    })
})
