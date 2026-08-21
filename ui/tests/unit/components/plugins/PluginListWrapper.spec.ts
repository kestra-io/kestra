import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn()}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1",
    apiUrlWithoutTenants: () => "/api/v1",
    baseUrl: "/",
}))

vi.mock("../../../../src/utils/tabTracking", () => ({
    trackPluginDocumentationView: vi.fn(),
}))

vi.mock("../../../../src/components/plugins/PluginList.vue", () => ({
    default: {template: "<div data-test='plugin-list' />"},
}))

let editorPluginValue: {cls: string} | null = null
let flowParsedValue: {id: string; namespace: string; description?: string} | undefined = undefined
let pluginsListValue: any[] = []

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({theme: "dark", configs: {pluginsHash: 0}}),
}))

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        get plugins() { return pluginsListValue },
        get editorPlugin() { return editorPluginValue },
        listWithSubgroup: vi.fn().mockResolvedValue(undefined),
    }),
}))

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        get flowParsed() { return flowParsedValue },
    }),
}))

const globalConfig = {
    plugins: [
        createI18n({
            legacy: false,
            locale: "en",
            fallbackWarn: false,
            missingWarn: false,
            messages: {en: {flow_description: "Flow Description", flow_no_description: "No description."}},
        }),
        KestraDesignSystem,
    ],
}

async function mountWrapper() {
    const {default: PluginListWrapper} = await import("../../../../src/components/plugins/PluginListWrapper.vue")
    const wrapper = mount(PluginListWrapper, {
        global: globalConfig,
    })
    await flushPromises()
    // KsMarkdown is an async component; wait for its loader to resolve.
    await vi.dynamicImportSettled()
    return wrapper
}

describe("PluginListWrapper flow-level documentation", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        editorPluginValue = null
        flowParsedValue = undefined
        pluginsListValue = []
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    test("shows flow description when no task is selected and flow has a description", async () => {
        // Given: editorPlugin is null and flow has a description
        editorPluginValue = null
        flowParsedValue = {id: "test", namespace: "io.kestra", description: "This flow processes data."}
        pluginsListValue = []

        // When: the component renders
        const wrapper = await mountWrapper()

        // Then: the flow description section is shown with the description text
        const flowDoc = wrapper.find(".flow-doc")
        expect(flowDoc.exists()).toBe(true)
        expect(wrapper.find(".flow-doc-title").exists()).toBe(true)
        expect(wrapper.find(".flow-doc-content").exists()).toBe(true)
    })

    test("shows empty state when flow has no description and no task is selected", async () => {
        // Given: editorPlugin is null and flow has no description
        editorPluginValue = null
        flowParsedValue = {id: "test", namespace: "io.kestra"}

        // When: the component renders
        const wrapper = await mountWrapper()

        // Then: the empty description state is shown
        const flowDoc = wrapper.find(".flow-doc")
        expect(flowDoc.exists()).toBe(true)
        const emptyEl = wrapper.find(".flow-doc-empty")
        expect(emptyEl.exists()).toBe(true)
    })

    test("hides flow doc when a task is selected (editorPlugin set)", async () => {
        // Given: a task is selected
        editorPluginValue = {cls: "io.kestra.plugin.core.flow.Subflow"}
        flowParsedValue = {id: "test", namespace: "io.kestra", description: "Some description."}

        // When: the component renders
        const wrapper = await mountWrapper()

        // Then: flow doc section is NOT shown (plugin docs take over)
        const flowDoc = wrapper.find(".flow-doc")
        expect(flowDoc.exists()).toBe(false)
    })

    test("hides flow doc when flowParsed is undefined (not in editor context)", async () => {
        // Given: no flow is loaded
        editorPluginValue = null
        flowParsedValue = undefined

        // When: the component renders
        const wrapper = await mountWrapper()

        // Then: flow doc section is NOT shown
        const flowDoc = wrapper.find(".flow-doc")
        expect(flowDoc.exists()).toBe(false)
    })

    test("plugin list is always rendered when not loading", async () => {
        // Given: any state
        editorPluginValue = null
        flowParsedValue = {id: "test", namespace: "io.kestra", description: "Test"}

        // When: the component renders
        const wrapper = await mountWrapper()

        // Then: the plugin list is also rendered
        expect(wrapper.find("[data-test='plugin-list']").exists()).toBe(true)
    })
})
