import {describe, test, expect, beforeEach, afterEach, vi} from "vitest"
import {defineComponent, h, ref, type PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import {useRoute, type LocationQuery} from "vue-router"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import type {Plugin} from "../../utils/pluginUtils"
import "../../utils/global"

const {scoped, extra, reset} = await vi.hoisted(async () => {
    const {ref} = await import("vue")
    return {scoped: ref(false), extra: ref<string[]>([]), reset: vi.fn()}
})

const names = (plugins: Plugin[]) => plugins.map(plugin => plugin.name).join(",")

const PluginList = (testId: string) => defineComponent({
    props: {
        plugins: {type: Array as PropType<Plugin[]>, default: () => []},
        catalog: {type: Array as PropType<Plugin[]>, default: () => []},
        entries: {type: Array as PropType<{key: string}[]>, default: () => []},
    },
    setup: (props) => () => h("div", {
        "data-test": testId,
        "data-plugins": names(props.plugins),
        "data-catalog": names(props.catalog),
        "data-entries": props.entries.map(entry => entry.key).join(","),
    }),
})

vi.mock("override/components/plugins/pluginCatalogExtension", async () => {
    const {markRaw} = await import("vue")
    return {
        usePluginCatalogExtension: () => ({
            toolbar: markRaw(PluginList("toolbar")),
            table: markRaw(PluginList("table")),
            extraEntries: () => extra.value.map(title => ({key: title, title})),
            extraCard: markRaw({props: ["entry"], setup: (props: {entry: {title: string}}) => () => h("h5", {"data-test": "extra-card"}, props.entry.title)}),
            matches: () => !scoped.value,
            emptyCopy: (filtered: boolean) => scoped.value && !filtered ? {title: "Nothing added", description: "Add one"} : undefined,
            reset: () => {
                reset()
                scoped.value = false
            },
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
vi.mock("vue-router", async () => {
    const {reactive} = await import("vue")
    const route = reactive({params: {}, query: {} as LocationQuery})
    return {
        useRoute: () => route,
        useRouter: () => ({push: vi.fn(), replace: vi.fn(async ({query}: {query: LocationQuery}) => {
            route.query = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined)) as LocationQuery
        })}),
    }
})
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
        useRoute().query = {}
        scoped.value = false
        extra.value = []
        reset.mockClear()
    })

    afterEach(() => {
        document.title = ""
    })

    test("hands the extension table the searched entries and the whole catalog, instead of the cards", async () => {
        useRoute().query = {view: "table"}
        const wrapper = await mountCatalog()

        await wrapper.find("input").setValue("s3.upload")
        await flushPromises()

        const table = wrapper.find("[data-test=table]")
        expect([table.attributes("data-plugins"), table.attributes("data-catalog")]).toEqual(["plugin-aws", "plugin-aws,plugin-dbt"])
        expect(wrapper.find("[data-test=toolbar]").attributes("data-catalog")).toBe("plugin-aws,plugin-dbt")
        expect(wrapper.find("article").exists()).toBe(false)
    })

    test("shows the empty state with Show all plugins instead of an empty table", async () => {
        useRoute().query = {view: "table"}
        const wrapper = await mountCatalog()

        await wrapper.find("input").setValue("nothing-matches")
        await flushPromises()

        expect(wrapper.find("[data-test=table]").exists()).toBe(false)
        expect(wrapper.find("[data-test=plugins-show-all]").exists()).toBe(true)
    })

    test("shows the cards while the Cards view is selected", async () => {
        const wrapper = await mountCatalog()

        expect(wrapper.find("[data-test=table]").exists()).toBe(false)
        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["AWS", "DBT"])
    })

    test("sorts the extension cards among the catalog cards, in both views", async () => {
        extra.value = ["Cloudflare R2"]
        const wrapper = await mountCatalog()

        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["AWS", "Cloudflare R2", "DBT"])

        useRoute().query = {sort: "nameDesc"}
        await flushPromises()
        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["DBT", "Cloudflare R2", "AWS"])

        useRoute().query = {sort: "nameDesc", view: "table"}
        await flushPromises()
        expect(wrapper.find("[data-test=table]").attributes("data-entries")).toBe("plugin-dbt,Cloudflare R2,plugin-aws")
    })

    test("shows the extension cards instead of the empty state", async () => {
        extra.value = ["storage-s3"]
        const wrapper = await mountCatalog()

        scoped.value = true
        await wrapper.find("input").setValue("aws")

        expect(wrapper.find("[data-test=plugins-empty]").exists()).toBe(false)
        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["storage-s3"])
    })

    test("lets the extension word an empty scope, and clears every filter on Show all plugins", async () => {
        scoped.value = true
        const wrapper = await mountCatalog()

        expect(wrapper.find("[data-test=plugins-empty]").text()).toContain("Nothing added")

        await wrapper.find("input").setValue("nothing-matches")
        expect(wrapper.find("[data-test=plugins-empty]").text()).toContain("pluginPage.empty.title")

        await wrapper.find("[data-test=plugins-show-all]").trigger("click")
        await flushPromises()

        expect(reset).toHaveBeenCalledOnce()
        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("")
        expect(wrapper.findAll("h5").map(title => title.text())).toEqual(["AWS", "DBT"])
    })
})
