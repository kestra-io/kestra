import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {reactive, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../i18nMount"

const mockRoute = reactive({
    query: {q: "first-query"},
    params: {},
    meta: {},
})

const search = vi.fn()
const searchFlowSuggestion = vi.fn()

const store = reactive({
    suggestedQuery: null,
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
    searchFlowSuggestion,
    reset: vi.fn(),
    retryNamespaceFiles: vi.fn(),
    truncatedFor: vi.fn(() => false),
    countFor: vi.fn(() => 0),
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

vi.mock("../../../../src/composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true)}),
}))

vi.mock("../../../../src/stores/crossResourceSearch", () => ({
    useCrossResourceSearchStore: () => store,
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    searchFlowsBySourceCode: vi.fn(),
}))

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({
        error: vi.fn(),
        success: vi.fn(),
        warning: vi.fn(),
    }),
}))

vi.mock("../../../../src/composables/useRouteContext", () => ({
    default: vi.fn(),
}))

import FlowsSearch from "../../../../src/components/flows/FlowsSearch.vue"

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
})
