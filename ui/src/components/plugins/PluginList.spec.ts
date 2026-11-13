import {describe, test, expect, beforeEach, vi} from "vitest"
import {defineComponent} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import PluginList from "./PluginList.vue"
import PluginDocumentation from "./PluginDocumentation.vue"
import {usePluginsStore} from "../../stores/plugins"
import type {Plugin} from "../../utils/pluginUtils"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn().mockResolvedValue({data: {}}), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({theme: "light", configs: {pluginsHash: 0}}),
}))

vi.mock("../../utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

vi.mock("../../composables/useScrollMemory", () => ({
    useScrollMemory: vi.fn(),
}))

vi.mock("./PluginDocumentation.vue", () => ({
    default: defineComponent({name: "PluginDocumentation", props: ["plugin"], template: "<div />"}),
}))

const CANONICAL = "io.kestra.plugin.slack.notifications.SlackIncomingWebhook"
const ALIAS = "io.kestra.plugin.slack.SlackIncomingWebhook"
const SCHEMA = {properties: {properties: {}}}

const CORE: Plugin = {
    name: "core",
    title: "Core",
    group: "io.kestra.plugin.core",
    subGroup: "io.kestra.plugin.core.log",
    tasks: [{cls: "io.kestra.plugin.core.log.Log"}],
}

const SLACK: Plugin = {
    name: "slack",
    title: "Slack",
    group: "io.kestra.plugin.slack",
    subGroup: "io.kestra.plugin.slack.notifications",
    version: "1.4.6",
    aliases: [ALIAS],
    tasks: [{cls: CANONICAL, aliases: [ALIAS]}],
}

describe("PluginList", () => {
    let pinia: ReturnType<typeof createPinia>

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        const store = usePluginsStore()
        store.plugins = [CORE, SLACK]
        store.load = vi.fn().mockResolvedValue({schema: SCHEMA})
    })

    const mountList = () => i18nMount(PluginList, {
        props: {plugins: [CORE, SLACK]},
        global: {
            plugins: [pinia],
            stubs: {TaskIcon: true, SearchField: true, PluginUnified: true},
        },
    })

    test("shouldFollowTheEditorToAnAliasedTypeWhenComingFromAnotherPlugin", async () => {
        usePluginsStore().editorPlugin = {cls: "io.kestra.plugin.core.log.Log", schema: SCHEMA}
        const wrapper = mountList()
        await flushPromises()
        expect(wrapper.text()).toContain("Log")

        usePluginsStore().editorPlugin = {cls: ALIAS, schema: SCHEMA}
        await flushPromises()

        expect(wrapper.text()).toContain("Slack")
        expect(wrapper.text()).not.toContain("Core")
    })

    test("shouldClearTheBreadcrumbWhenTheEditorTypeMatchesNoListedPlugin", async () => {
        usePluginsStore().editorPlugin = {cls: "io.kestra.plugin.core.log.Log", schema: SCHEMA}
        const wrapper = mountList()
        await flushPromises()
        expect(wrapper.text()).toContain("Core")

        usePluginsStore().editorPlugin = {cls: "io.kestra.plugin.unknown.Mystery", schema: SCHEMA}
        await flushPromises()

        expect(wrapper.text()).not.toContain("Core")
    })

    test("shouldKeepThePinnedVersionWhenDocumentationIsReloaded", async () => {
        usePluginsStore().editorPlugin = {cls: CANONICAL, version: "1.2.0", schema: SCHEMA}
        const wrapper = mountList()
        await flushPromises()

        expect(wrapper.getComponent(PluginDocumentation).props("plugin")).toMatchObject({cls: CANONICAL, version: "1.2.0"})
    })
})
