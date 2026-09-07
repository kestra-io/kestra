import {describe, test, expect, beforeEach, afterEach, vi} from "vitest"
import {ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import PluginCatalog from "../../../../src/components/plugins/PluginCatalog.vue"
import "../../../../src/utils/global"

vi.mock("axios", () => ({
    default: {get: vi.fn().mockResolvedValue({data: []})},
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn()}),
}))

// Shape captured from GET /api/v1/plugins/groups/subgroups with plugin-script-python and
// plugin-dbt installed (kestra-io/kestra#17888): python's only subgroup is the bundled legacy
// package whose single task is deprecated, while dbt has two real, visible subgroups.
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
                {cls: "io.kestra.plugin.dbt.cloud.TriggerRun"},
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
            tasks: [
                {cls: "io.kestra.plugin.dbt.cloud.CheckStatus"},
                {cls: "io.kestra.plugin.dbt.cloud.TriggerRun"},
            ],
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

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("../../../../src/composables/usePluginsCount", () => ({
    usePluginsCount: () => ({totalPlugins: ref(900)}),
}))

vi.mock("../../../../src/stores/pluginsEnrichment", () => ({
    usePluginsEnrichmentStore: () => ({fetchEnrichment: vi.fn(), getEnrichment: () => undefined}),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {en: {}},
})

async function mountCatalog() {
    const wrapper = mount(PluginCatalog, {
        global: {
            plugins: [i18n, KestraDesignSystem],
            stubs: {TopNavBar: true, TaskIcon: true},
        },
    })
    await flushPromises()
    return wrapper
}

describe("PluginCatalog", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.title = ""
    })

    test("shows the main card of a plugin whose only subgroup holds deprecated tasks", async () => {
        const wrapper = await mountCatalog()
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).toContain("Python")
        expect(titles).not.toContain("Scripts")

        const pythonCard = wrapper.findAll("article").find(card => card.find("h5").text() === "Python")!
        expect(pythonCard.text()).toContain("4")
    })

    test("shows only the subgroup cards of a plugin with visible subgroups", async () => {
        const wrapper = await mountCatalog()
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).toContain("Dbt CLI")
        expect(titles).toContain("Dbt Cloud")
        expect(titles).not.toContain("DBT")
    })
})
