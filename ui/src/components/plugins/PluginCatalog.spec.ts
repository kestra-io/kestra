import {describe, test, expect, beforeEach, afterEach, vi} from "vitest"
import {ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {useRoute, type LocationQuery} from "vue-router"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import PluginCatalog from "./PluginCatalog.vue"
import {usePluginsEnrichmentStore} from "../../stores/pluginsEnrichment"
import "../../utils/global"

const navigation = vi.hoisted(() => ({delayMs: 0, replaces: 0}))

vi.mock("../../utils/publicApi", () => ({
    publicFetchJson: vi.fn().mockResolvedValue([]),
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

vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    const route = reactive({params: {}, query: {} as LocationQuery})
    return {
        useRoute: () => route,
        useRouter: () => ({push: vi.fn(), replace: vi.fn(async ({query}: {query: LocationQuery}) => {
            navigation.replaces++
            await new Promise(resolve => setTimeout(resolve, navigation.delayMs))
            route.query = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined)) as LocationQuery
        })}),
    }
})

vi.mock("../../composables/usePluginsCount", () => ({
    usePluginsCount: () => ({totalPlugins: ref(900)}),
}))

async function mountCatalog() {
    const wrapper = i18nMount(PluginCatalog, {
        global: {
            plugins: [KestraDesignSystem],
            stubs: {TopNavBar: true, TaskIcon: true},
        },
    })
    await flushPromises()
    return wrapper
}

describe("PluginCatalog", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        useRoute().query = {}
        navigation.delayMs = 0
        navigation.replaces = 0
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

    test("shows one card per plugin artifact, not one per subgroup", async () => {
        const wrapper = await mountCatalog()
        const titles = wrapper.findAll("h5").map(t => t.text())

        expect(titles).toEqual(["DBT", "Python"])
    })

    test("finds the artifact card when searching for a task of one of its subgroups", async () => {
        const wrapper = await mountCatalog()

        await wrapper.find("input").setValue("cloud.triggerrun")
        await flushPromises()

        expect(wrapper.findAll("h5").map(t => t.text())).toEqual(["DBT"])
    })

    test("finds the artifact card when searching for the title of one of its subgroups", async () => {
        const wrapper = await mountCatalog()

        await wrapper.find("input").setValue("dbt cloud")
        await flushPromises()

        expect(wrapper.findAll("h5").map(t => t.text())).toEqual(["DBT"])
    })

    test("keeps every character typed while a navigation is pending, and writes the search to the URL once typing pauses", async () => {
        navigation.delayMs = 50
        const wrapper = await mountCatalog()
        const input = wrapper.find("input")

        for (const text of ["d", "db", "dbt"]) {
            (input.element as HTMLInputElement).value = text
            await input.trigger("input")
        }
        await new Promise(resolve => setTimeout(resolve, 400))
        await flushPromises()

        expect((input.element as HTMLInputElement).value).toBe("dbt")
        expect(wrapper.findAll("h5").map(t => t.text())).toEqual(["DBT"])
        expect(useRoute().query.q).toBe("dbt")
        expect(navigation.replaces).toBe(1)
    })

    test("restores the search and the sort from the URL when the catalog is opened again", async () => {
        useRoute().query = {q: "dbt", sort: "nameDesc"}

        const wrapper = await mountCatalog()

        expect(wrapper.find("input").element.value).toBe("dbt")
        expect(wrapper.findAll("h5").map(t => t.text())).toEqual(["DBT"])
    })

    test("sorts the cards by the title they show", async () => {
        usePluginsEnrichmentStore().enrichments["io.kestra.plugin.dbt"] = {title: "Zeta dbt"}

        const wrapper = await mountCatalog()

        expect(wrapper.findAll("h5").map(t => t.text())).toEqual(["Python", "Zeta dbt"])
    })
})
