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

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            cancel: "Cancel",
            source_search: {
                confirm_bar_message: "Replace {matches} across {flows} editable flows. {skipped} read-only flows will be skipped.",
                diff_preview_aria: "Replacement diff preview",
                diff_preview_label: "diff preview · not yet applied",
                line_label: "line {line}",
                match_count: "{count} match | {count} matches",
                open_in_editor: "Open in editor",
                preview_empty: "Select a result to preview. Click a flow in the results list to see its source.",
                preview_error: "Failed to load flow source",
                replace_all: "Replace all",
            },
        },
    },
})

function createGlobal() {
    setActivePinia(createPinia())
    return {
        plugins: [i18n, KestraDesignSystem],
    }
}

function baseProps(overrides: Record<string, unknown> = {}) {
    return {
        selected: null,
        query: "",
        replaceMode: false,
        previewResponse: null,
        selectionSummary: null,
        readOnlyExcludedCount: 0,
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

    test("shows empty state when no flow is selected", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: baseProps(),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='source-search-preview']").exists()).toBe(true)
        expect(mockLoadFlow).not.toHaveBeenCalled()
        expect(wrapper.html()).toContain("Select a result to preview.")
    })

    test("fetches source via store using the selected namespace and id", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: ns"})

        mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "my-flow", line: 1}, query: "my-flow"}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "ns", id: "my-flow", store: false})
    })

    test("renders editor and highlights the selected line after successful load", async () => {
        const source = "id: my-flow\nnamespace: ns\ntasks: []"
        mockLoadFlow.mockResolvedValue({source})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "my-flow", line: 2}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)
        expect(mockRevealLineInCenter).toHaveBeenCalledWith(2)
        expect(mockCreateDecorationsCollection).toHaveBeenCalledWith([
            expect.objectContaining({range: {startLineNumber: 2, startColumn: 1, endLineNumber: 2, endColumn: 1}}),
        ])
    })

    test("shows error state when loadFlow rejects", async () => {
        mockLoadFlow.mockRejectedValue(new Error("404 Not Found"))

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "missing-flow", line: 1}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.html()).toContain("Failed to load flow source")
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
    })

    test("resets to empty state when selected becomes null", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "flow", line: 1}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)

        await wrapper.setProps({selected: null})
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
        expect(wrapper.html()).toContain("Select a result to preview.")
    })

    test("refetches source when selected flow changes to a different flow", async () => {
        mockLoadFlow
            .mockResolvedValueOnce({source: "id: flow-a\nnamespace: ns"})
            .mockResolvedValueOnce({source: "id: flow-b\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "flow-a", line: 1}, query: ""}),
            global: createGlobal(),
        })
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(1)

        await wrapper.setProps({selected: {namespace: "ns", id: "flow-b", line: 1}})
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(2)
        expect(mockLoadFlow).toHaveBeenLastCalledWith({namespace: "ns", id: "flow-b", store: false})
    })

    test("re-highlights without a second loadFlow call when the selected line changes on the same flow", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nextract: a\nextract: b"})

        const wrapper = mount(SourceSearchPreview, {
            props: baseProps({selected: {namespace: "ns", id: "flow", line: 2}, query: "extract"}),
            global: createGlobal(),
        })
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(1)
        expect(mockRevealLineInCenter).toHaveBeenLastCalledWith(2)

        await wrapper.setProps({selected: {namespace: "ns", id: "flow", line: 3}})
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledTimes(1)
        expect(mockRevealLineInCenter).toHaveBeenLastCalledWith(3)
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
                selected: {namespace: "ns", id: "flow", line: 3},
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
                selected: {namespace: "ns", id: "flow", line: 3},
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
})
