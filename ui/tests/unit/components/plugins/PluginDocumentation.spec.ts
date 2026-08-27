import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import PluginDocumentation from "../../../../src/components/plugins/PluginDocumentation.vue"
import {usePluginsStore} from "../../../../src/stores/plugins"

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({
        theme: "light",
    }),
}))

vi.mock("../../../../src/components/plugins/schema/shikiToolset", () => ({
    getHighlighterCore: vi.fn().mockResolvedValue({
        codeToHtml: () => "<code>mock code</code>",
    }),
}))

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            plugins: {
                nav_overview: "Overview",
                nav_properties: "Properties",
                nav_outputs: "Outputs",
                nav_examples: "Examples",
                copy_type: "Copy type",
            },
        },
    },
})

const CLS = "io.kestra.plugin.scripts.python.Script"
const SCHEMA = {properties: {properties: {script: {type: "string"}}}}

describe("PluginDocumentation", () => {
    let pinia: ReturnType<typeof createPinia>

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
    })

    const mountWithPlugin = async (plugin: Record<string, unknown>) => {
        usePluginsStore().editorPlugin = plugin as never

        const wrapper = mount(PluginDocumentation, {
            global: {
                plugins: [pinia, i18n, KestraDesignSystem],
                stubs: {TaskIcon: {template: "<span />"}},
            },
        })

        await flushPromises()
        return wrapper
    }

    const setPlugin = async (plugin: Record<string, unknown>) => {
        usePluginsStore().editorPlugin = plugin as never
        await flushPromises()
    }

    test("shouldKeepSectionChipsWhenPluginObjectIsReplacedWithSameClsAndVersion", async () => {
        const wrapper = await mountWithPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA})
        expect(wrapper.find("[data-test='plugin-doc-navchip-properties']").exists()).toBe(true)

        await setPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA})

        expect(wrapper.find("[data-test='plugin-doc-navchip-properties']").exists()).toBe(true)
    })

    test("shouldKeepSectionChipsWhenVersionResolvesFromUndefinedForSameCls", async () => {
        const wrapper = await mountWithPlugin({cls: CLS, version: undefined, schema: SCHEMA})
        expect(wrapper.find("[data-test='plugin-doc-navchip-properties']").exists()).toBe(true)

        await setPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA})

        expect(wrapper.find("[data-test='plugin-doc-navchip-properties']").exists()).toBe(true)
    })

    test("shouldReturnToOverviewWhenPluginVersionChanges", async () => {
        const wrapper = await mountWithPlugin({cls: CLS, version: undefined, schema: SCHEMA})
        await wrapper.get("[data-test='plugin-doc-navchip-properties']").trigger("click")
        expect(wrapper.get("[data-test='plugin-doc-navchip-properties']").classes()).toContain("active")

        await setPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA})

        expect(wrapper.get("[data-test='plugin-doc-navchip-overview']").classes()).toContain("active")
    })
})
