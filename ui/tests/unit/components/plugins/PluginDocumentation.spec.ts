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

vi.mock("@kestra-io/design-system/shiki", () => ({
    getShiki: vi.fn().mockResolvedValue({
        codeToHtml: () => "<code>mock code</code>",
        getLoadedLanguages: () => ["yaml"],
    }),
    loadLanguageOnDemand: vi.fn().mockResolvedValue(false),
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
                nav_metrics: "Metrics",
                nav_definitions: "Definitions",
                copy_type: "Copy type",
            },
        },
    },
})

const CLS = "io.kestra.plugin.scripts.python.Script"
const SCHEMA = {properties: {properties: {script: {type: "string"}}}}
const SCHEMA_WITH_METRICS_AND_DEFINITIONS = {
    properties: {
        properties: {script: {type: "string"}},
        $metrics: [{name: "cache.hit", type: "counter"}],
    },
    definitions: {
        "io.kestra.plugin.scripts.exec.scripts.models.DockerOptions": {
            title: "DockerOptions",
            properties: {image: {type: "string"}},
        },
    },
}

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

    test("shouldShowMetricsAndDefinitionsSectionsWhenTheirChipsAreSelected", async () => {
        const wrapper = await mountWithPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA_WITH_METRICS_AND_DEFINITIONS})

        // jsdom caches computed styles across v-show toggles, so visibility is asserted on the inline style v-show writes
        const isShown = (selector: string) => !(wrapper.get(selector).attributes("style") ?? "").includes("display: none")

        expect(isShown("[data-section='metrics']")).toBe(false)
        expect(isShown("[data-section='definitions']")).toBe(false)

        await wrapper.get("[data-test='plugin-doc-navchip-metrics']").trigger("click")
        expect(isShown("[data-section='metrics']")).toBe(true)
        expect(isShown("[data-section='definitions']")).toBe(false)
        expect(wrapper.text()).toContain("cache.hit")

        await wrapper.get("[data-test='plugin-doc-navchip-definitions']").trigger("click")
        expect(isShown("[data-section='metrics']")).toBe(false)
        expect(isShown("[data-section='definitions']")).toBe(true)
        expect(wrapper.text()).toContain("DockerOptions")
    })

    test("shouldReturnToOverviewWhenPluginVersionChanges", async () => {
        const wrapper = await mountWithPlugin({cls: CLS, version: undefined, schema: SCHEMA})
        await wrapper.get("[data-test='plugin-doc-navchip-properties']").trigger("click")
        expect(wrapper.get("[data-test='plugin-doc-navchip-properties']").classes()).toContain("active")

        await setPlugin({cls: CLS, version: "1.0.0", schema: SCHEMA})

        expect(wrapper.get("[data-test='plugin-doc-navchip-overview']").classes()).toContain("active")
    })
})
