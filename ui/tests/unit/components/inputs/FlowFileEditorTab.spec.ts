import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"

vi.mock("../../../../src/stores/flow", async () => {
    const {reactive} = await import("vue")
    const store = reactive({
        flowYaml: "",
        flowYamlOrigin: "",
        previewSource: undefined as string | undefined,
        flow: {namespace: "company.team", id: "flow_1"},
        isReadOnly: false,
        isCreating: false,
        executeFlow: false,
        onEdit: vi.fn(),
        saveAll: vi.fn(),
    })
    return {useFlowStore: () => store}
})

vi.mock("../../../../src/composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

vi.mock("../../../../src/composables/playground/useFlowEditorRunTaskButton", async () => {
    const {ref} = await import("vue")
    return {
        default: () => ({
            playgroundStore: {enabled: false},
            highlightHoveredTask: vi.fn(),
            highlightedLines: ref(undefined),
        }),
    }
})

vi.mock("../../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({
        allTypes: [],
        editorPlugin: undefined,
        lazyLoadSchemaType: vi.fn(),
        updateDocumentation: vi.fn(),
    }),
}))

vi.mock("../../../../src/stores/doc", () => ({useDocStore: () => ({docId: ""})}))
vi.mock("../../../../src/stores/productTour", () => ({useProductTourStore: () => ({isGuidedActive: false})}))
vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({readFile: vi.fn().mockResolvedValue({content: ""})}),
}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}, openCopilot: vi.fn()})}))

vi.mock("@kestra-io/topology", () => ({
    flowYamlUtils: {getTypeAtPosition: vi.fn(), getVersionAtPosition: vi.fn(), parse: vi.fn()},
}))

vi.mock("../../../../src/components/inputs/FileExplorer.vue", () => ({
    FILES_CLOSE_TAB_INJECTION_KEY: Symbol("files-close-tab-injection-key"),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal() as Record<string, unknown>
    return {
        ...actual,
        KsEditor: {
            name: "KsEditor",
            template: "<div data-test=\"ks-editor\" />",
            props: ["modelValue", "original", "readOnly", "lang", "schemaType", "navbar", "path", "options"],
            emits: ["update:modelValue"],
        },
    }
})

vi.mock("vue-router", () => ({
    useRouter: () => ({push: vi.fn()}),
    useRoute: () => ({query: {}, params: {}}),
}))

// The tab resolves the locked-line tooltip through useI18n, which throws
// outside an app that installed the plugin. Same stub as useApplyDraft.spec.ts.
vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

import {useFlowStore} from "../../../../src/stores/flow"
import FlowFileEditorTab from "../../../../src/components/inputs/FlowFileEditorTab.vue"

const BUFFER = "id: flow_1\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n"
const MUTATED = `${BUFFER}labels:\n  managed-by: governance\n`

function mountTab(flow = true) {
    return mount(FlowFileEditorTab, {
        props: {name: "Flow.yaml", extension: "yaml", path: "Flow.yaml", flow, dirty: false},
    })
}

function editor(wrapper: ReturnType<typeof mountTab>) {
    return wrapper.findComponent({name: "KsEditor"})
}

describe("FlowFileEditorTab policy mutation preview", () => {
    let flowStore: {flowYaml: string; flowYamlOrigin: string; previewSource: string | undefined; isReadOnly: boolean}

    beforeEach(() => {
        flowStore = useFlowStore() as unknown as typeof flowStore
        flowStore.flowYaml = BUFFER
        flowStore.flowYamlOrigin = BUFFER
        flowStore.previewSource = undefined
        flowStore.isReadOnly = false
    })

    test("shouldEditTheBufferDirectlyWhenNoPreviewSource", () => {
        const wrapper = mountTab()

        expect(editor(wrapper).props("modelValue")).toBe(BUFFER)
        expect(editor(wrapper).props("original")).toBeUndefined()
        expect(editor(wrapper).props("readOnly")).toBe(false)
    })

    test("shouldDiffPreviewAgainstBufferWhenPreviewSourceIsSet", async () => {
        const wrapper = mountTab()

        flowStore.previewSource = MUTATED
        await flushPromises()

        // `original` is what makes KsEditor build a Monaco diff editor, so the mutated
        // lines are what the user actually sees.
        expect(editor(wrapper).props("original")).toBe(BUFFER)
        expect(editor(wrapper).props("modelValue")).toBe(MUTATED)
        expect(editor(wrapper).props("readOnly")).toBe(true)
    })

    test("shouldRestoreTheBufferWhenPreviewSourceIsCleared", async () => {
        const wrapper = mountTab()

        flowStore.previewSource = MUTATED
        await flushPromises()
        flowStore.previewSource = undefined
        await flushPromises()

        expect(editor(wrapper).props("original")).toBeUndefined()
        expect(editor(wrapper).props("modelValue")).toBe(BUFFER)
        expect(editor(wrapper).props("readOnly")).toBe(false)
    })

    test("shouldKeepBufferUntouchedWhenEditorEmitsWhilePreviewing", async () => {
        const wrapper = mountTab()

        flowStore.previewSource = MUTATED
        await flushPromises()
        await editor(wrapper).vm.$emit("update:modelValue", MUTATED)

        expect(flowStore.flowYaml).toBe(BUFFER)
    })

    test("shouldStayReadOnlyForPreviewWhenFlowIsAlreadyReadOnly", async () => {
        flowStore.isReadOnly = true
        const wrapper = mountTab()

        expect(editor(wrapper).props("readOnly")).toBe(true)

        flowStore.previewSource = MUTATED
        await flushPromises()

        expect(editor(wrapper).props("readOnly")).toBe(true)
    })

    test("shouldIgnorePreviewSourceForNamespaceFilesWhenTabIsNotTheFlow", async () => {
        const wrapper = mountTab(false)

        flowStore.previewSource = MUTATED
        await flushPromises()

        expect(editor(wrapper).props("original")).toBeUndefined()
        expect(editor(wrapper).props("modelValue")).toBe("")
    })
})
