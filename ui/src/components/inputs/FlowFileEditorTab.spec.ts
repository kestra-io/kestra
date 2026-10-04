import {describe, expect, it, vi, beforeEach} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent, ref} from "vue"
import FlowFileEditorTab from "./FlowFileEditorTab.vue"

const fileMetadata = vi.fn()
const readFile = vi.fn()
const saveOrCreateFile = vi.fn()

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "io.kestra.test"}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}))
vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({fileMetadata, readFile, saveOrCreateFile}),
}))
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {pluginsHash: 0}, openCopilot: vi.fn()}),
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flow: {namespace: "io.kestra.test"}, isReadOnly: false, isCreating: false, flowYaml: "", flowYamlOrigin: "", previewSource: undefined}),
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
vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({triggerExecution: vi.fn(), execution: undefined}),
}))
vi.mock("../../stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
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
                big_file_download_only: "This file is {size}. It is too large to open in the editor, download it instead.",
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
        saveOrCreateFile.mockReset()
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

    it("should load the file content when the size stats are unavailable", async () => {
        fileMetadata.mockRejectedValue(new Error("stats unavailable"))

        const wrapper = mountTab()
        await flushPromises()

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"})
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false)
    })

    it("should send a single save when saving again while the previous save is still pending", async () => {
        fileMetadata.mockResolvedValue({size: 1024})
        let finishSave: () => void = () => {}
        saveOrCreateFile.mockReturnValue(new Promise<void>((resolve) => finishSave = resolve))

        const wrapper = mountTab()
        await flushPromises()
        const editor = wrapper.findComponent({name: "KsEditor"})
        editor.vm.$emit("update:model-value", "edited content")

        editor.vm.$emit("save")
        editor.vm.$emit("save")
        editor.vm.$emit("save")
        finishSave()
        await flushPromises()

        expect(saveOrCreateFile).toHaveBeenCalledTimes(1)
        expect(saveOrCreateFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt", content: "edited content"})
    })

    it("should send the newer content when saving again after editing during a pending save", async () => {
        fileMetadata.mockResolvedValue({size: 1024})
        let finishSave: () => void = () => {}
        saveOrCreateFile.mockReturnValueOnce(new Promise<void>((resolve) => finishSave = resolve)).mockResolvedValue(undefined)

        const wrapper = mountTab()
        await flushPromises()
        const editor = wrapper.findComponent({name: "KsEditor"})
        editor.vm.$emit("update:model-value", "edited content")
        editor.vm.$emit("save")
        editor.vm.$emit("update:model-value", "edited again")
        editor.vm.$emit("save")
        finishSave()
        await flushPromises()

        expect(saveOrCreateFile).toHaveBeenCalledTimes(2)
        expect(saveOrCreateFile).toHaveBeenLastCalledWith({namespace: "io.kestra.test", path: "data.txt", content: "edited again"})
    })
})
