import {describe, test, expect, vi, beforeEach} from "vitest"
import {onMounted} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

const {
    mockLoadFlow,
    mockRevealLineInCenter,
    mockClearDecoration,
    mockCreateDecorationsCollection,
    mockGetEditor,
} = vi.hoisted(() => {
    const mockRevealLineInCenter = vi.fn()
    const mockClearDecoration = vi.fn()
    const mockCreateDecorationsCollection = vi.fn(() => ({clear: mockClearDecoration}))
    const mockGetEditor = vi.fn(() => ({
        revealLineInCenter: mockRevealLineInCenter,
        createDecorationsCollection: mockCreateDecorationsCollection,
    }))
    const mockLoadFlow = vi.fn()
    return {
        mockLoadFlow,
        mockRevealLineInCenter,
        mockClearDecoration,
        mockCreateDecorationsCollection,
        mockGetEditor,
    }
})

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        loadFlow: mockLoadFlow,
    }),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal() as Record<string, unknown>
    return {
        ...actual,
        KsEditor: {
            name: "KsEditor",
            template: "<div class=\"ks-editor-mock\" data-test=\"ks-editor\"></div>",
            props: ["modelValue", "lang", "readOnly", "navbar"],
            emits: ["editorMounted"],
            setup(_props: unknown, {emit, expose}: {emit: (e: string, ...args: unknown[]) => void; expose: (api: Record<string, unknown>) => void}) {
                expose({
                    focus: vi.fn(),
                    destroy: vi.fn(),
                    getEditor: mockGetEditor,
                })
                onMounted(() => emit("editorMounted", mockGetEditor()))
                return {}
            },
        },
    }
})

vi.mock("vue-router", () => ({
    useRouter: () => ({push: vi.fn()}),
    useRoute: () => ({query: {}, params: {}}),
    RouterLink: {
        template: "<a><slot /></a>",
        props: ["to"],
    },
}))

import SourceSearchPreview from "../../../../src/components/flows/SourceSearchPreview.vue"
import en from "../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", messages: en})

function createGlobal() {
    setActivePinia(createPinia())
    return {
        plugins: [i18n, KestraDesignSystem],
    }
}

function baseProps(overrides: Record<string, unknown> = {}) {
    return {
        selection: null,
        query: "",
        caseSensitive: false,
        replaceMode: false,
        previewResponse: null,
        selectionSummary: null,
        readOnlyExcludedCount: 0,
        excludedFromReplaceCount: 0,
        kvEntry: null,
        ...overrides,
    }
}

describe("SourceSearchPreview", () => {
    beforeEach(() => {
        mockLoadFlow.mockReset()
        mockRevealLineInCenter.mockReset()
        mockClearDecoration.mockReset()
        mockCreateDecorationsCollection.mockClear()
    })

    test("shows empty state when nothing is selected", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: baseProps(),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='source-search-preview']").exists()).toBe(true)
        expect(mockLoadFlow).not.toHaveBeenCalled()
        expect(wrapper.html()).toContain("Select a result to preview.")
    })

    test("fetches source via store for a flows selection", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: ns"})

        mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "flows", namespace: "ns", id: "my-flow", line: 1, column: 0}, query: "my-flow"}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "ns", id: "my-flow", store: false})
    })

    test("renders editor and highlights the selected line after successful load", async () => {
        const source = "id: my-flow\nnamespace: ns\ntasks: []"
        mockLoadFlow.mockResolvedValue({source})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "flows", namespace: "ns", id: "my-flow", line: 2, column: 0}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)
        expect(mockRevealLineInCenter).toHaveBeenCalledWith(2)
    })

    test("shows error state when loadFlow rejects", async () => {
        mockLoadFlow.mockRejectedValue(new Error("404 Not Found"))

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "flows", namespace: "ns", id: "missing-flow", line: 1, column: 0}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.html()).toContain("Failed to load flow source")
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
    })

    test("resets to empty state when selection becomes null", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "flows", namespace: "ns", id: "flow", line: 1, column: 0}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)

        await wrapper.setProps({selection: null})
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
        expect(wrapper.html()).toContain("Select a result to preview.")
    })

    test("renders the diff preview and confirm bar in replace mode", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns\nprojectId: analytics-prod\n"})

        const previewResponse = {
            totalMatches: 1,
            totalFlows: 1,
            editableFlowCount: 1,
            flows: [
                {
                    namespace: "ns",
                    id: "flow",
                    editable: true,
                    matches: [{line: 3, before: "projectId: analytics-prod", after: "projectId: analytics-eu"}],
                },
            ],
        }

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({
                selection: {type: "flows", namespace: "ns", id: "flow", line: 3, column: 0},
                query: "analytics-prod",
                replaceMode: true,
                previewResponse,
                selectionSummary: {selectedFlowCount: 1, selectedMatchCount: 1},
                readOnlyExcludedCount: 2,
            }),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
        expect(wrapper.text()).toContain("analytics-prod")
        expect(wrapper.text()).toContain("analytics-eu")
        expect(wrapper.find(".source-search-preview__confirm-bar").exists()).toBe(true)
    })

    test("emits cancel and replace-all from the confirm bar", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns\nprojectId: analytics-prod\n"})

        const previewResponse = {
            totalMatches: 1,
            totalFlows: 1,
            editableFlowCount: 1,
            flows: [
                {
                    namespace: "ns",
                    id: "flow",
                    editable: true,
                    matches: [{line: 3, before: "projectId: analytics-prod", after: "projectId: analytics-eu"}],
                },
            ],
        }

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({
                selection: {type: "flows", namespace: "ns", id: "flow", line: 3, column: 0},
                query: "analytics-prod",
                replaceMode: true,
                previewResponse,
                selectionSummary: {selectedFlowCount: 1, selectedMatchCount: 1},
                readOnlyExcludedCount: 0,
            }),
            global: createGlobal(),
        })
        await flushPromises()

        const buttons = wrapper.findAll(".source-search-preview__confirm-bar button")
        await buttons[0].trigger("click")
        expect(wrapper.emitted("cancel")).toBeTruthy()

        await buttons[1].trigger("click")
        expect(wrapper.emitted("replace-all")).toBeTruthy()
    })

    test("renders a metadata card for a namespace file selection without calling loadFlow", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "files", namespace: "company.data", path: "scripts/extract.py"}, query: "extract"}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).not.toHaveBeenCalled()
        expect(wrapper.find("[data-test='source-search-preview-meta']").exists()).toBe(true)
        expect(wrapper.text()).toContain("company.data")
        expect(wrapper.text()).toContain("File content is not searched")
    })

    test("renders a metadata card for a KV selection with the value withheld", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({
                selection: {type: "kv", namespace: "company.data", key: "landing-bucket"},
                query: "bucket",
                kvEntry: {key: "landing-bucket", updateDate: "2026-08-07T00:00:00Z"},
            }),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.text()).toContain("Not shown")
        expect(wrapper.text()).toContain("Updated")
    })

    test("renders a metadata card for a secret selection and never shows a value", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selection: {type: "secrets", namespace: "company.data", key: "aws-access-key"}, query: "aws"}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.text()).toContain("Never shown or searched")
    })
})
