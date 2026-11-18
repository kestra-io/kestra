import {describe, test, expect, vi, beforeEach} from "vitest"
import {reactive, ref} from "vue"
import type {LocationQuery} from "vue-router"
import {Comparators, type AppliedFilter, type FilterConfiguration} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {useFilterGroups} from "../../../../src/components/Data/KsDataTable/filter/composables/useFilterGroups"
import {useDismissedKeys} from "../../../../src/components/Data/KsDataTable/filter/composables/useDismissedKeys"
import {usePreAppliedFilters} from "../../../../src/components/Data/KsDataTable/filter/composables/usePreAppliedFilters"
import {useRouteSync} from "../../../../src/components/Data/KsDataTable/filter/composables/useRouteSync"

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

    test("a root-level filter the configuration has no key for survives a route write, while a configured one is rewritten from the tree", () => {
        const {tree, sync} = setup()
        tree.replaceTree([
            {id: "g1", kind: "leaf", filters: [makeFilter({key: "namespace", value: "my-ns"})]},
        ])

        mockRoute.query = {
            "filters[tags][CONTAINS]": ["ops", "report"],
            "filters[namespace][EQUALS]": "stale",
            "filters[flowId][EQUALS]": "stale",
        }
        sync.updateRoute()

        expect(mockPush).toHaveBeenCalledWith({
            query: {
                "filters[tags][CONTAINS]": ["ops", "report"],
                "filters[namespace][EQUALS]": "my-ns",
            },
        })
    })

    test("the time range's own date params and a grouped key with no configured field are still cleared", () => {
        const {sync} = setup({
            configuration: {
                title: "Time range",
                keys: [{key: "timeRange", label: "Interval", valueType: "time-range", comparators: [Comparators.EQUALS]}],
            },
        })

        mockRoute.query = {
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-01-01T00:00:00.000Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-01-02T00:00:00.000Z",
            dateFilter: "startDate",
            "filters[or][1][tags][CONTAINS]": "ops",
        }
        sync.updateRoute()

        expect(mockPush).toHaveBeenCalledWith({query: {}})
    })
})
