import {describe, expect, it, vi, beforeEach} from "vitest";
import {flushPromises, mount} from "@vue/test-utils";
import {createI18n} from "vue-i18n";
import {ref} from "vue";
import EditorWrapper from "./EditorWrapper.vue";
import Utils from "../../utils/utils";

const fileMetadata = vi.fn();
const readFile = vi.fn();
const downloadFile = vi.fn();

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "io.kestra.test"}, query: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}));
vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
}));
vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({fileMetadata, readFile, downloadFile, saveOrCreateFile: vi.fn()}),
}));
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}));
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {pluginsHash: 0}}),
}));
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({flow: undefined, isReadOnly: false, isCreating: false, flowYaml: "", flowYamlOrigin: "", openAiCopilot: false, setOpenAiCopilot: vi.fn(), onEdit: vi.fn()}),
    isSuccessfulFlowSaveOutcome: () => false,
}));
vi.mock("../../stores/plugins", () => ({
    usePluginsStore: () => ({lazyLoadSchemaType: vi.fn(), editorPlugin: undefined, allTypes: [], updateDocumentation: vi.fn()}),
}));
vi.mock("../../stores/api", () => ({
    useApiStore: () => ({posthogEvents: vi.fn()}),
}));
vi.mock("../../stores/onboardingV2", () => ({
    useOnboardingV2Store: () => ({isGuidedActive: false, recordSave: vi.fn()}),
}));
vi.mock("../../composables/playground/useFlowEditorRunTaskButton", () => ({
    default: () => ({playgroundStore: {enabled: false}, highlightHoveredTask: vi.fn(), highlightedLines: ref(undefined)}),
}));
vi.mock("@kestra-io/ui-libs/flow-yaml-utils", () => ({
    getTypeAtPosition: vi.fn(),
    getVersionAtPosition: vi.fn(),
}));
vi.mock("./Editor.vue", () => ({
    default: {name: "EditorStub", template: "<div data-test=\"editor\" />"},
}));
vi.mock("../ai/AiCopilot.vue", () => ({
    default: {name: "AiCopilot", template: "<div />"},
}));
vi.mock("../ai/AITriggerButton.vue", () => ({
    default: {name: "AITriggerButton", template: "<div />"},
}));
vi.mock("./PlaygroundRunTaskButton.vue", () => ({
    default: {name: "PlaygroundRunTaskButton", template: "<div />"},
}));
vi.mock("./AcceptDecline.vue", () => ({
    default: {name: "AcceptDecline", template: "<div />"},
}));
vi.mock("./FileExplorer.vue", () => ({
    FILES_CLOSE_TAB_INJECTION_KEY: Symbol("files-close-tab-injection-key"),
}));

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
            "namespace files": {
                read_only: "Read only",
            },
        },
    },
});

function mountTab() {
    return mount(EditorWrapper, {
        props: {name: "data.txt", extension: "txt", path: "data.txt", flow: false, dirty: false},
        global: {
            plugins: [i18n],
            stubs: {
                "el-alert": {template: "<div><slot /></div>"},
                "el-button": {template: "<button v-bind=\"$attrs\"><slot /></button>"},
                "el-tooltip": {template: "<div><slot /></div>"},
            },
        },
    });
}

describe("EditorWrapper", () => {
    beforeEach(() => {
        fileMetadata.mockReset();
        readFile.mockReset();
        downloadFile.mockReset();
        readFile.mockResolvedValue({content: "file content"});
        downloadFile.mockResolvedValue(new Blob(["file content"]));
        window.URL.createObjectURL = vi.fn(() => "blob:kestra-test");
        vi.spyOn(Utils, "downloadUrl").mockImplementation(() => {});
    });

    it("should load the file content when the file is below the size threshold", async () => {
        fileMetadata.mockResolvedValue({size: 1024});

        const wrapper = mountTab();
        await flushPromises();

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"});
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false);
        expect(wrapper.find("[data-test=\"editor\"]").exists()).toBe(true);
    });

    it("should show a download-only warning instead of loading the content when the file exceeds the size threshold", async () => {
        fileMetadata.mockResolvedValue({size: 11 * 1024 * 1024});

        const wrapper = mountTab();
        await flushPromises();

        expect(readFile).not.toHaveBeenCalled();
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(true);
        expect(wrapper.find("[data-test=\"editor\"]").exists()).toBe(false);

        await wrapper.find("[data-test=\"big-file-download\"]").trigger("click");
        await flushPromises();

        expect(downloadFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"});
        expect(Utils.downloadUrl).toHaveBeenCalledWith("blob:kestra-test", "data.txt");
    });

    it("should load the file content when the size stats are unavailable", async () => {
        fileMetadata.mockRejectedValue(new Error("stats unavailable"));

        const wrapper = mountTab();
        await flushPromises();

        expect(readFile).toHaveBeenCalledWith({namespace: "io.kestra.test", path: "data.txt"});
        expect(wrapper.find("[data-test=\"big-file-warning\"]").exists()).toBe(false);
    });
});
