import {describe, test, expect, beforeEach, afterEach, vi} from "vitest"
import {defineComponent, h, ref, type PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import type {Plugin} from "../../utils/pluginUtils"
import "../../utils/global"

const {showTable} = vi.hoisted(() => ({showTable: {value: true}}))

const names = (plugins: Plugin[]) => plugins.map(plugin => plugin.name).join(",")

const PluginList = (testId: string) => defineComponent({
    props: {plugins: {type: Array as PropType<Plugin[]>, default: () => []}, catalog: {type: Array as PropType<Plugin[]>, default: () => []}},
    setup: (props) => () => h("div", {"data-test": testId, "data-plugins": names(props.plugins), "data-catalog": names(props.catalog)}),
})

vi.mock("override/components/plugins/pluginCatalogExtension", async () => {
    const {markRaw} = await import("vue")
    return {
        usePluginCatalogExtension: () => ({
            toolbar: markRaw(PluginList("toolbar")),
            table: markRaw(PluginList("table")),
            showTable: () => showTable.value,
        }),
    }
})

vi.mock("../../utils/publicApi", () => ({publicFetchJson: vi.fn().mockResolvedValue([])}))
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn()})}))
vi.mock("@kestra-io/kestra-sdk/plugins", () => ({
    pluginBySubgroups: vi.fn().mockResolvedValue([
        {name: "plugin-aws", title: "AWS", group: "io.kestra.plugin.aws", tasks: [{cls: "io.kestra.plugin.aws.s3.Upload"}]},
        {name: "plugin-dbt", title: "DBT", group: "io.kestra.plugin.dbt", tasks: [{cls: "io.kestra.plugin.dbt.cli.DbtCLI"}]},
    ]),
}))
vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1", apiUrlWithoutTenants: () => "/api/v1", baseUrl: "/"}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({theme: "light", loadConfigs: vi.fn()})}))
vi.mock("vue-router", () => ({useRoute: () => ({params: {}, query: {}}), useRouter: () => ({push: vi.fn()})}))
vi.mock("../../composables/usePluginsCount", () => ({usePluginsCount: () => ({totalPlugins: ref(900)})}))
vi.mock("../../stores/pluginsEnrichment", () => ({
    usePluginsEnrichmentStore: () => ({fetchEnrichment: vi.fn(), getEnrichment: () => undefined}),
}))

import PluginCatalog from "./PluginCatalog.vue"

async function mountCatalog() {
    const wrapper = i18nMount(PluginCatalog, {global: {plugins: [KestraDesignSystem], stubs: {TopNavBar: true, TaskIcon: true}}})
    await flushPromises()
    return wrapper
}

describe("PluginCatalog extension", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        showTable.value = true
    })

    afterEach(() => {
        document.title = ""
    })

    test("hands the extension table the searched entries and the whole catalog, instead of the cards", async () => {
        const wrapper = await mountCatalog()

        await wrapper.find("input").setValue("s3.upload")
        await flushPromises()

        const table = wrapper.find("[data-test=table]")
        expect([table.attributes("data-plugins"), table.attributes("data-catalog")]).toEqual(["plugin-aws", "plugin-aws,plugin-dbt"])
        expect(wrapper.find("[data-test=toolbar]").attributes("data-catalog")).toBe("plugin-aws,plugin-dbt")
        expect(wrapper.find("article").exists()).toBe(false)
    })

    test("shows the cards while the extension table is off", async () => {
        showTable.value = false
        const wrapper = await mountCatalog()

        expect(wrapper.find("[data-test=table]").exists()).toBe(false)
        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["AWS", "DBT"])
    })
})
