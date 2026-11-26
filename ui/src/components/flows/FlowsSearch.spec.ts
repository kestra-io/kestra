import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {reactive, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

const mockRoute = reactive<{
    query: Record<string, string | string[] | undefined>
    params: Record<string, string | undefined>
    meta: Record<string, unknown>
}>({
    query: {q: "first-query"},
    params: {},
    meta: {},
})

const search = vi.fn()

const store = reactive({
    suggestedQuery: null as string | null,
    flows: {
        status: "done",
        results: [],
    },
    files: {
        status: "idle",
        namespaces: [],
    },
    kv: {
        status: "idle",
        groups: [],
    },
    secrets: {
        status: "idle",
        groups: [],
    },
    flatSelections: [],
    filesNamespacesDone: 0,
    filesNamespacesTotal: 0,
    filesNamespacesFailed: [],
    search,
    searchSuggestion: vi.fn(),
    reset: vi.fn(),
    retryNamespaceFiles: vi.fn(),
    truncatedFor: vi.fn(() => false),
    countFor: vi.fn((_type?: string) => 0),
    totalFor: vi.fn(() => 0),
    resourceCountFor: vi.fn(() => 0),
    statusFor: vi.fn(() => "idle"),
    errorMessageFor: vi.fn(() => ""),
})

vi.mock("vue-router", () => ({
    useRoute: () => mockRoute,
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
    }),
}))

vi.mock("../../composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true)}),
}))

vi.mock("../../stores/crossResourceSearch", () => ({
    useCrossResourceSearchStore: () => store,
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlowsBySourceCode: vi.fn(),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({
        error: vi.fn(),
        success: vi.fn(),
        warning: vi.fn(),
    }),
}))

vi.mock("../../composables/useRouteContext", () => ({
    default: vi.fn(),
}))

import FlowsSearch from "./FlowsSearch.vue"

describe("FlowsSearch", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.useFakeTimers()
        mockRoute.query = {q: "first-query"}
        store.suggestedQuery = null

        search.mockImplementation(() => new Promise((resolve) => {
            setTimeout(() => resolve(1), 0)
        }))
    })

    afterEach(() => {
        vi.useRealTimers()
        document.title = ""
    })

    it("keeps the loading state when a previous search resolves after the query changes", async () => {
        let resolveSecondSearch!: (value: number) => void

        search
            .mockResolvedValueOnce(1)
            .mockImplementationOnce(
                () =>
                    new Promise((resolve) => {
                        resolveSecondSearch = resolve
                    }),
            )
        const wrapper = i18nMount(FlowsSearch, {
            global: {
                stubs: {
                    TopNavBar: true,
                    NamespaceSelect: true,
                    SourceSearchResults: true,
                    SourceSearchPreview: true,
                },
            },
        })

        // Let the initial search finish.
        await flushPromises()

        // Start a second query.
        mockRoute.query.q = "second-query"
        await flushPromises()
        await vi.advanceTimersByTimeAsync(300)
        await flushPromises()

        // The second search is now in flight.
        expect(search).toHaveBeenCalledTimes(2)

        // Change the query again while the second search is still running.
        mockRoute.query.q = "third-query"
        await flushPromises()

        // Resolve the stale second search.
        resolveSecondSearch(1)
        await flushPromises()

        // The stale search must not clear the loading state.
        expect(wrapper.find("[data-test=\"source-search-loading\"]").exists()).toBe(true)

        wrapper.unmount()
    })

    it("dispatches suggestion for empty types in a partial match and renders the suggestion banner", async () => {
        search.mockResolvedValueOnce(1)
        mockRoute.query = {q: "eu-west-2", types: "flows,secrets"}
        store.countFor = vi.fn((type?: string) => (type === "flows" ? 2 : 0))
        store.flows.results = [{namespace: "ns", id: "flow", editable: true, matches: [{line: 1, column: 1, snippet: "eu-west-2"}]}] as never

        const wrapper = i18nMount(FlowsSearch, {
            messages: {
                source_search: {
                    did_you_mean: "Did you mean {suggestion}?",
                },
            },
            global: {
                stubs: {
                    TopNavBar: true,
                    NamespaceSelect: true,
                    SourceSearchResults: true,
                    SourceSearchPreview: true,
                },
            },
        })

        await flushPromises()

        expect(store.searchSuggestion).toHaveBeenCalledWith(
            expect.objectContaining({
                query: "eu-west-2",
                types: ["secrets"],
            }),
            1,
        )

        store.suggestedQuery = "eu_west_2"
        await flushPromises()

        const banner = wrapper.find("[data-test=\"source-search-suggestion-banner\"]")
        expect(banner.exists()).toBe(true)
        expect(banner.find("[data-test=\"source-search-suggestion-button\"]").text()).toBe("eu_west_2")

        wrapper.unmount()
    })
})
