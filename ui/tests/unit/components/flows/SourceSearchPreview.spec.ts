import {describe, test, expect, vi, beforeEach} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"

const mockLoadFlow = vi.fn()

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
            template: "<div class=\"ks-editor-mock\" data-test=\"ks-editor\"><slot /></div>",
            props: ["modelValue", "lang", "readOnly", "navbar"],
            expose: ["focus", "destroy", "highlightLinesRange", "clearLinesRangeHighlights", "getEditor"],
            setup() {
                return {
                    focus: vi.fn(),
                    destroy: vi.fn(),
                    highlightLinesRange: vi.fn(),
                    clearLinesRangeHighlights: vi.fn(),
                    getEditor: vi.fn(),
                }
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
                preview_empty: "Select a result to preview — click on a flow in the results list to preview its source here",
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

describe("SourceSearchPreview", () => {
    beforeEach(() => {
        mockLoadFlow.mockReset()
    })

    test("shows empty state when no flow is selected", async () => {
        const wrapper = mount(SourceSearchPreview, {
            props: {selected: null, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.find("[data-test='source-search-preview']").exists()).toBe(true)
        expect(mockLoadFlow).not.toHaveBeenCalled()
        expect(wrapper.html()).toContain("Select a result to preview")
        expect(wrapper.html()).toContain("click on a flow in the results list")
    })

    test("fetches source via store using the selected namespace and id", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: ns"})

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "my-flow"}, query: "my-flow"},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "ns", id: "my-flow", store: false})
    })

    test("renders editor with source after successful load", async () => {
        const source = "id: my-flow\nnamespace: ns\ntasks: []"
        mockLoadFlow.mockResolvedValue({source})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "my-flow"}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        const editor = wrapper.find("[data-test='ks-editor']")
        expect(editor.exists()).toBe(true)
    })

    test("shows error state when loadFlow rejects", async () => {
        mockLoadFlow.mockRejectedValue(new Error("404 Not Found"))

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "missing-flow"}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(wrapper.html()).toContain("Failed to load flow source")
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
    })

    test("resets to empty state when selected becomes null", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: flow\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow"}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(true)

        await wrapper.setProps({selected: null})
        await flushPromises()
        expect(wrapper.find("[data-test='ks-editor']").exists()).toBe(false)
        expect(wrapper.html()).toContain("Select a result to preview")
        expect(wrapper.html()).toContain("click on a flow in the results list")
    })

    test("refetches source when selected flow changes", async () => {
        mockLoadFlow
            .mockResolvedValueOnce({source: "id: flow-a\nnamespace: ns"})
            .mockResolvedValueOnce({source: "id: flow-b\nnamespace: ns"})

        const wrapper = mount(SourceSearchPreview, {
            props: {selected: {namespace: "ns", id: "flow-a"}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(1)

        await wrapper.setProps({selected: {namespace: "ns", id: "flow-b"}})
        await flushPromises()
        expect(mockLoadFlow).toHaveBeenCalledTimes(2)
        expect(mockLoadFlow).toHaveBeenLastCalledWith({namespace: "ns", id: "flow-b", store: false})
    })

    test("handles namespace with dots correctly by using the structured prop", async () => {
        mockLoadFlow.mockResolvedValue({source: "id: my-flow\nnamespace: company.data"})

        mount(SourceSearchPreview, {
            props: {selected: {namespace: "company.data", id: "my-flow"}, query: ""},
            global: createGlobal(),
        })
        await flushPromises()

        expect(mockLoadFlow).toHaveBeenCalledWith({namespace: "company.data", id: "my-flow", store: false})
    })
})
