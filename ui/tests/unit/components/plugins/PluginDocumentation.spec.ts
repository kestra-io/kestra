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

describe("PluginDocumentation watcher", () => {
    let pinia: ReturnType<typeof createPinia>

    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
    })

    const globalConfig = () => ({
        plugins: [pinia, i18n, KestraDesignSystem],
        stubs: {
            TaskIcon: {
                template: "<span data-testid='task-icon' />",
            },
        },
    })

    test("does not clear sectionCounts when editorPlugin is updated with a new object reference for the same cls and version", async () => {
        const pluginsStore = usePluginsStore()
        const initialPlugin = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "1.0.0",
            schema: {
                properties: {
                    properties: {
                        script: {type: "string"},
                    },
                },
            },
        }

        pluginsStore.editorPlugin = initialPlugin as any

        const wrapper = mount(PluginDocumentation, {
            global: globalConfig(),
        })

        await flushPromises()

        // Verify that properties chip appears after section-counts emission
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")

        // Simulate pluginsStore.updateDocumentation creating a NEW object reference with identical cls and version
        pluginsStore.editorPlugin = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "1.0.0",
            schema: initialPlugin.schema,
        } as any

        await flushPromises()

        // sectionCounts must NOT be cleared back to {} merely because object reference changed
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")
    })

    test("resets sectionCounts when plugin class (cls) changes", async () => {
        const pluginsStore = usePluginsStore()
        const plugin1 = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "1.0.0",
            schema: {
                properties: {
                    properties: {
                        script: {type: "string"},
                    },
                },
            },
        }

        pluginsStore.editorPlugin = plugin1 as any

        const wrapper = mount(PluginDocumentation, {
            global: globalConfig(),
        })

        await flushPromises()
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")

        // Change cls to a different plugin class
        pluginsStore.editorPlugin = {
            cls: "io.kestra.plugin.scripts.python.Commands",
            version: "1.0.0",
            schema: {},
        } as any

        await flushPromises()

        // sectionCounts should be reset so section chips are cleared until new section-counts emission
        expect(wrapper.find(".dp-nav").text()).not.toContain("Properties")
    })

    test("resets sectionCounts when plugin version changes", async () => {
        const pluginsStore = usePluginsStore()
        const pluginV1 = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "1.0.0",
            schema: {
                properties: {
                    properties: {
                        script: {type: "string"},
                    },
                },
            },
        }

        pluginsStore.editorPlugin = pluginV1 as any

        const wrapper = mount(PluginDocumentation, {
            global: globalConfig(),
        })

        await flushPromises()
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")

        // Change version to a different version of the same plugin class
        pluginsStore.editorPlugin = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "2.0.0",
            schema: {},
        } as any

        await flushPromises()

        // sectionCounts should be reset when version changes
        expect(wrapper.find(".dp-nav").text()).not.toContain("Properties")
    })

    test("does not clear sectionCounts when version transitions from undefined to a defined version for the same cls", async () => {
        const pluginsStore = usePluginsStore()
        const initialPlugin = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: undefined,
            schema: {
                properties: {
                    properties: {
                        script: {type: "string"},
                    },
                },
            },
        }

        pluginsStore.editorPlugin = initialPlugin as any

        const wrapper = mount(PluginDocumentation, {
            global: globalConfig(),
        })

        await flushPromises()
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")

        // Transition version from undefined to a defined version string ("1.0.0")
        pluginsStore.editorPlugin = {
            cls: "io.kestra.plugin.scripts.python.Script",
            version: "1.0.0",
            schema: initialPlugin.schema,
        } as any

        await flushPromises()

        // sectionCounts must NOT be cleared when version transitions from undefined to defined
        expect(wrapper.find(".dp-nav").text()).toContain("Properties")
    })
})
