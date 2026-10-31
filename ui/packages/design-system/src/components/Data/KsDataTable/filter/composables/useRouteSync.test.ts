import {describe, test, expect, vi, beforeEach} from "vitest"
import {reactive, ref} from "vue"
import type {LocationQuery} from "vue-router"
import {Comparators, type AppliedFilter, type FilterConfiguration} from "../utils/filterTypes"
import {SEARCH_QUERY_KEY} from "../utils/constants"
import {useFilterGroups} from "./useFilterGroups"
import {useDismissedKeys} from "./useDismissedKeys"
import {usePreAppliedFilters} from "./usePreAppliedFilters"
import {useRouteSync} from "./useRouteSync"

const mockPush = vi.fn()
const mockRoute = reactive<{query: LocationQuery}>({query: {}})

vi.mock("vue-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-router")>()
    return {
        ...actual,
        useRoute: () => mockRoute,
        useRouter: () => ({push: mockPush}),
    }
})

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "namespace",
    keyLabel: "Namespace",
    comparator: Comparators.EQUALS,
    comparatorLabel: "Equals",
    value: "test",
    valueLabel: "test",
    ...overrides,
})

const defaultConfiguration: FilterConfiguration = {
    title: "Test filters",
    keys: [
        {
            key: "namespace",
            label: "Namespace",
            visibleByDefault: true,
            valueType: "text",
            comparators: [Comparators.EQUALS],
        },
        {
            key: "flowId",
            label: "Flow ID",
            visibleByDefault: true,
            valueType: "text",
            comparators: [Comparators.EQUALS],
        },
    ],
}

interface SetupOptions {
    configuration?: FilterConfiguration;
    tree?: ReturnType<typeof useFilterGroups>;
    showSearchInput?: boolean;
}

const setup = (options: SetupOptions = {}) => {
    const configuration = options.configuration ?? defaultConfiguration
    const tree = options.tree ?? useFilterGroups()
    const dismissed = useDismissedKeys(configuration)
    const preApplied = usePreAppliedFilters()
    const searchQuery = ref("")
    const showSearchInput = options.showSearchInput ?? false

    const sync = useRouteSync({
        configuration,
        tree,
        dismissed,
        searchQuery,
        preApplied,
        showSearchInput,
    })

    return {tree, dismissed, preApplied, searchQuery, sync}
}

describe("useRouteSync", () => {
    beforeEach(() => {
        mockPush.mockClear()
        mockRoute.query = {}
    })

    test("a filter with no value is dropped before the route is written", () => {
        const {tree, sync} = setup()
        tree.replaceTree([
            {
                id: "g1",
                kind: "leaf",
                filters: [
                    makeFilter({key: "namespace", value: "my-ns"}),
                    makeFilter({key: "flowId", value: ""}),
                ],
            },
        ])

        sync.updateRoute()

        expect(mockPush).toHaveBeenCalledWith({
            query: {
                "filters[namespace][EQUALS]": "my-ns",
            },
        })
    })

    test("a leaf left with no filters is removed", () => {
        const {tree, sync} = setup()
        tree.replaceTree([
            {
                id: "g1",
                kind: "leaf",
                filters: [
                    makeFilter({key: "namespace", value: ""}),
                ],
            },
        ])

        sync.updateRoute()

        expect(mockPush).toHaveBeenCalledWith({
            query: {},
        })
    })

    test("a wrapper reduced to a single child is unwrapped into that child", () => {
        const {tree, sync} = setup()
        tree.replaceTree([
            {
                id: "w1",
                kind: "wrapper",
                logical: "AND",
                children: [
                    {
                        id: "c1",
                        kind: "leaf",
                        filters: [makeFilter({key: "namespace", value: "my-ns"})],
                    },
                    {
                        id: "c2",
                        kind: "leaf",
                        filters: [makeFilter({key: "flowId", value: ""})],
                    },
                ],
            },
        ])

        sync.updateRoute()

        expect(mockPush).toHaveBeenCalledWith({
            query: {
                "filters[namespace][EQUALS]": "my-ns",
            },
        })
    })

    test("updateRoute with shouldResetPage clears the page parameter, and leaves it alone otherwise", () => {
        const {sync} = setup()

        mockRoute.query = {page: "3"}
        sync.updateRoute(true)
        expect(mockPush).toHaveBeenCalledWith({
            query: {},
        })

        mockPush.mockClear()
        mockRoute.query = {page: "3"}
        sync.updateRoute(false)
        expect(mockPush).toHaveBeenCalledWith({
            query: {page: "3"},
        })
    })

    test("a non-empty trimmed search query is written, and a blank or whitespace-only one is removed", () => {
        const {searchQuery, sync} = setup({showSearchInput: true})

        searchQuery.value = "  my search query  "
        sync.updateRoute()
        expect(mockPush).toHaveBeenCalledWith({
            query: {
                [SEARCH_QUERY_KEY]: "my search query",
            },
        })

        mockPush.mockClear()
        mockRoute.query = {
            [SEARCH_QUERY_KEY]: "my search query",
            q: "legacy-q",
            search: "legacy-search",
        }
        searchQuery.value = "   "
        sync.updateRoute()
        expect(mockPush).toHaveBeenCalledWith({
            query: {},
        })
    })

    test("initializeFromRoute merges the default visible filters into the first leaf without duplicating a key already present in the route", () => {
        mockRoute.query = {
            "filters[namespace][EQUALS]": "existing-ns",
        }
        const {tree} = setup()

        const leaf = tree.groups.value[0]
        expect(leaf.kind).toBe("leaf")
        if (leaf.kind !== "wrapper") {
            expect(leaf.filters).toHaveLength(2)
            expect(leaf.filters.map((f) => f.key)).toEqual(["namespace", "flowId"])
            expect(leaf.filters.find((f) => f.key === "namespace")?.value).toBe("existing-ns")
        }
    })

    test("a route that already matches the live tree does not trigger another write", () => {
        const tree = useFilterGroups()
        tree.replaceTree([
            {
                id: "g1",
                kind: "leaf",
                filters: [makeFilter({key: "namespace", value: "test-ns"})],
            },
        ], "OR")

        mockRoute.query = {
            "filters[namespace][EQUALS]": "test-ns",
        }

        const replaceSpy = vi.spyOn(tree, "replaceTree")

        setup({tree})

        expect(replaceSpy).not.toHaveBeenCalled()
        expect(mockPush).not.toHaveBeenCalled()
    })

    test("hasUnrenderableFilters is true when the query holds a key the configuration cannot render", () => {
        mockRoute.query = {
            "filters[or][0][and][0][or][0][namespace][EQUALS]": "test",
        }
        const {sync} = setup()

        expect(sync.hasUnrenderableFilters.value).toBe(true)

        mockRoute.query = {
            "filters[namespace][EQUALS]": "test",
        }
        expect(sync.hasUnrenderableFilters.value).toBe(false)
    })

    test("applyRawQuery replaces the filter parameters, drops page, and leaves unrelated query keys intact", () => {
        mockRoute.query = {
            page: "2",
            size: "25",
            sort: "id:asc",
            "filters[oldKey][EQUALS]": "oldValue",
        }
        const {sync} = setup()

        sync.applyRawQuery("filters[newKey][EQUALS]=newValue")

        expect(mockPush).toHaveBeenCalledWith({
            query: {
                size: "25",
                sort: "id:asc",
                "filters[newKey][EQUALS]": "newValue",
            },
        })
    })
})
