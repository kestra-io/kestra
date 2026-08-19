import {describe, expect, it, vi, beforeEach} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent, ref} from "vue"
import FlowFileEditorTab from "./FlowFileEditorTab.vue"

const fileMetadata = vi.fn()
const readFile = vi.fn()

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "io.kestra.test"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))
vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({fileMetadata, readFile, saveOrCreateFile: vi.fn()}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {pluginsHash: 0}, openCopilot: vi.fn()}),
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flow: undefined, isReadOnly: false, isCreating: false, flowYaml: "", flowYamlOrigin: "", previewSource: undefined}),
}))
vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({lazyLoadSchemaType: vi.fn(), editorPlugin: undefined, allTypes: [], updateDocumentation: vi.fn()}),
}))
vi.mock("../../stores/doc", () => ({
    useDocStore: () => ({docId: undefined}),
}))
vi.mock("../../stores/productTour", () => ({
    useProductTourStore: () => ({isGuidedActive: false}),
}))
vi.mock("../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))
vi.mock("../../composables/playground/useFlowEditorRunTaskButton", () => ({
    default: () => ({playgroundStore: {enabled: false}, highlightHoveredTask: vi.fn(), highlightedLines: ref(undefined)}),
}))
vi.mock("@kestra-io/topology", () => ({
    flowYamlUtils: {getTypeAtPosition: vi.fn(), getVersionAtPosition: vi.fn()},
}))
vi.mock("@kestra-io/design-system", () => ({
    KsEditor: defineComponent({name: "KsEditor", template: "<div data-test=\"ks-editor\" />"}),
}))
vi.mock("./PlaygroundRunTaskButton.vue", () => ({
    default: defineComponent({name: "PlaygroundRunTaskButton", template: "<div />"}),
}))
vi.mock("./FileExplorer.vue", () => ({
    FILES_CLOSE_TAB_INJECTION_KEY: Symbol("files-close-tab-injection-key"),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {
        en: {
            download: "Download",
            file_preview: {
                big_file_warning: "This file is {size}. Do you want to load it anyway?",
                load_anyway: "Load anyway",
            },
        },
    },
})

function mountTab() {
    return mount(FlowFileEditorTab, {
        props: {name: "data.txt", extension: "txt", path: "data.txt", flow: false, dirty: false},
        global: {
            plugins: [i18n],
            stubs: {
                KsAlert: {template: "<div><slot /></div>"},
                KsButtonGroup: {template: "<div><slot /></div>"},
                KsButton: {template: "<button v-bind=\"$attrs\"><slot /></button>"},
            },
        },
    })
}

describe("FlowFileEditorTab", () => {
    beforeEach(() => {
        fileMetadata.mockReset()
        readFile.mockReset()
        readFile.mockResolvedValue({content: "file content"})
    })

    it("should load the file content when the file is below the size threshold", async () => {
        fileMetadata.mockResolvedValue({size: 1024})

        const wrapper = mountTab()
        await flushPromises()

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"})
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"ks-editor\"]").exists()).toBe(true)
    })

    it("should show a download-only warning instead of loading the content when the file exceeds the size threshold", async () => {
        fileMetadata.mockResolvedValue({size: 11 * 1024 * 1024})

        const wrapper = mountTab()
        await flushPromises()

        expect(readFile).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"ks-editor\"]").exists()).toBe(false)

        const downloadLink = wrapper.findAll("button").find((button) => button.attributes("href") !== undefined)
        expect(downloadLink?.attributes("href")).toBe("/api/v1/main/namespaces/io.kestra.test/files?path=/data.txt")
        expect(downloadLink?.attributes("download")).toBe("data.txt")
    })

    it("should load the content when the user clicks load anyway", async () => {
        fileMetadata.mockResolvedValue({size: 11 * 1024 * 1024})

        const wrapper = mountTab()
        await flushPromises()
        expect(readFile).not.toHaveBeenCalled()

        await wrapper.find("[data-test=\"big-file-load-anyway\"]").trigger("click")
        await flushPromises()

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"})
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"ks-editor\"]").exists()).toBe(true)
    })

    it("should load the file content when the size stats are unavailable", async () => {
        fileMetadata.mockRejectedValue(new Error("stats unavailable"))

        const wrapper = mountTab()
        await flushPromises()

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"})
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false)
    })
})
