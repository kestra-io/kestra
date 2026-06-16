import {describe, test, expect, vi, beforeEach} from "vitest"
import {onMounted} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

const {
    mockLoadFlow,
    mockSetSelection,
    mockRevealRangeInCenter,
    mockFindMatches,
    mockGetModel,
    mockClearDecoration,
    mockCreateDecorationsCollection,
    mockGetEditor,
} = vi.hoisted(() => {
    const mockFindMatches = vi.fn()
    const mockGetModel = vi.fn(() => ({findMatches: mockFindMatches}))
    const mockSetSelection = vi.fn()
    const mockRevealRangeInCenter = vi.fn()
    const mockClearDecoration = vi.fn()
    const mockCreateDecorationsCollection = vi.fn(() => ({clear: mockClearDecoration}))
    const mockGetEditor = vi.fn(() => ({
        getModel: mockGetModel,
        setSelection: mockSetSelection,
        revealRangeInCenter: mockRevealRangeInCenter,
        createDecorationsCollection: mockCreateDecorationsCollection,
    }))
    const mockLoadFlow = vi.fn()
    return {
        mockLoadFlow,
        mockSetSelection,
        mockRevealRangeInCenter,
        mockFindMatches,
        mockGetModel,
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
                    highlightLinesRange: vi.fn(),
                    clearLinesRangeHighlights: vi.fn(),
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
}))

import SourceSearchPreview from "../../../../src/components/flows/SourceSearchPreview.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            source_search: {
                match_count: "{count} match | {count} matches",
                open_flow: "Open flow",
                preview_empty: "Select a result to preview. Click a flow in the results list to see its source.",
                preview_error: "Failed to load flow source",
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

const makeRange = (line: number) => ({startLineNumber: line, endLineNumber: line, startColumn: 1, endColumn: 10})

describe("SourceSearchPreview", () => {
    beforeEach(() => {
        mockLoadFlow.mockReset()
        mockSetSelection.mockReset()
        mockRevealRangeInCenter.mockReset()
        mockFindMatches.mockReset()
        mockClearDecoration.mockReset()
        mockCreateDecorationsCollection.mockClear()
        mockGetModel.mockReturnValue({findMatches: mockFindMatches})
    })

    test("shows empty state when no flow is selected", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: {selected: null, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='source-search-preview']").exists()).toBe(true)
        expect(mockLoadFlow).not.toHaveBeenCalled()
        expect(wrapper.html()).toContain("Select a result to preview.")
        expect(wrapper.html()).toContain("Click a flow in the results list to see its source.")
    })

    test("fetches source via store using the selected namespace and id", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: ns"})

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "my-flow", matchIndex: 0}, query: "my-flow"},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "ns", id: "my-flow", store: false})
    })

    test("renders editor with source after successful load", async () => {
        const source = "id: my-flow\nnamespace: ns\ntasks: []"
        mockLoadFlow.mockResolvedValue({source})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "my-flow", matchIndex: 0}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        const editor = wrapper.find("[data-test='ks-editor']")
        expect(editor.exists()).toBe(true)
    })

    test("shows error state when loadFlow rejects", async () => {
        mockLoadFlow.mockRejectedValue(new Error("404 Not Found"))

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "missing-flow", matchIndex: 0}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.html()).toContain("Failed to load flow source")
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
    })

    test("resets to empty state when selected becomes null", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow", matchIndex: 0}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)

        await wrapper.setProps({selected: null})
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
        expect(wrapper.html()).toContain("Select a result to preview.")
        expect(wrapper.html()).toContain("Click a flow in the results list to see its source.")
    })

    test("refetches source when selected flow changes to a different flow", async () => {
        mockLoadFlow
            .mockResolvedValueOnce({source: "id: flow-a\nnamespace: ns"})
            .mockResolvedValueOnce({source: "id: flow-b\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow-a", matchIndex: 0}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(1)

        await wrapper.setProps({selected: {namespace: "ns", id: "flow-b", matchIndex: 0}})
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(2)
        expect(mockLoadFlow).toHaveBeenLastCalledWith({namespace: "ns", id: "flow-b", store: false})
    })

    test("highlights the matchIndex-th occurrence when flow loads with a query", async () => {
        const source = "id: my-flow\nextract: something\nextract: again"
        mockLoadFlow.mockResolvedValue({source})
        mockFindMatches.mockReturnValue([
            {range: makeRange(2)},
            {range: makeRange(3)},
        ])

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "my-flow", matchIndex: 1}, query: "extract"},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockSetSelection).toHaveBeenCalledWith(makeRange(3))
        expect(mockRevealRangeInCenter).toHaveBeenCalledWith(makeRange(3))
        expect(mockCreateDecorationsCollection).toHaveBeenCalledWith([
            expect.objectContaining({range: makeRange(3)}),
        ])
    })

    test("clamps matchIndex to the last available match when index exceeds matches length", async () => {
        const source = "id: flow\nextract: only-one"
        mockLoadFlow.mockResolvedValue({source})
        mockFindMatches.mockReturnValue([{range: makeRange(2)}])

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow", matchIndex: 5}, query: "extract"},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockSetSelection).toHaveBeenCalledWith(makeRange(2))
    })

    test("re-highlights without a second loadFlow call when matchIndex changes on the same flow", async () => {
        const source = "id: flow\nextract: a\nextract: b"
        mockLoadFlow.mockResolvedValue({source})
        mockFindMatches.mockReturnValue([
            {range: makeRange(2)},
            {range: makeRange(3)},
        ])

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow", matchIndex: 0}, query: "extract"},
            global: createGlobal(),
        })
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(1)
        expect(mockSetSelection).toHaveBeenLastCalledWith(makeRange(2))

        await wrapper.setProps({selected: {namespace: "ns", id: "flow", matchIndex: 1}})
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledTimes(1)
        expect(mockSetSelection).toHaveBeenLastCalledWith(makeRange(3))
    })

    test("handles namespace with dots correctly by using the structured prop", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: company.data"})

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "company.data", id: "my-flow", matchIndex: 0}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "company.data", id: "my-flow", store: false})
    })
})
