import {describe, test, expect} from "vitest"
import {ref} from "vue"
import {Comparators, type AppliedFilter, type FilterConfiguration} from "../../../../src/components/Data/KsDataTable/filter/utils/filterTypes"
import {useFilterGroups} from "../../../../src/components/Data/KsDataTable/filter/composables/useFilterGroups"
import {useDismissedKeys} from "../../../../src/components/Data/KsDataTable/filter/composables/useDismissedKeys"
import {usePreAppliedFilters} from "../../../../src/components/Data/KsDataTable/filter/composables/usePreAppliedFilters"
import {useFilterActions} from "../../../../src/components/Data/KsDataTable/filter/composables/useFilterActions"

const makeFilter = (overrides: Partial<AppliedFilter> = {}): AppliedFilter => ({
    id: "f1",
    key: "key",
    keyLabel: "Key",
    comparator: Comparators.CONTAINS,
    comparatorLabel: "Contains",
    value: "foo",
    valueLabel: "foo",
    ...overrides,
})

const setup = () => {
    const tree = useFilterGroups()
    const configuration: FilterConfiguration = {
        title: "Test filters",
        keys: [
            {
                key: "key",
                label: "Key",
                visibleByDefault: true,
                valueType: "text",
                comparators: [Comparators.CONTAINS],
            },
            {
                key: "other",
                label: "Other",
                visibleByDefault: true,
                valueType: "text",
                comparators: [Comparators.CONTAINS],
            },
        ],
    }
    const dismissed = useDismissedKeys(configuration)
    const preApplied = usePreAppliedFilters()
    const searchQuery = ref("")
    const updateRoute = () => {}
    const hasValue = () => true

    const actions = useFilterActions({
        tree,
        dismissed,
        preApplied,
        searchQuery,
        updateRoute,
        hasValue,
    })

    return {tree, dismissed, preApplied, actions}
}

describe("useFilterActions - pre-applied cache eviction", () => {
    test("clearFilters evicts the pre-applied cache entirely", () => {
        // Given
        const {preApplied, actions} = setup()
        const filter = makeFilter()
        preApplied.markAsPreApplied([filter])
        expect(preApplied.hasPreApplied("key")).toBe(true)

        // When
        actions.clearFilters()

        // Then
        expect(preApplied.hasPreApplied("key")).toBe(false)
    })

    test("removeFilter evicts the pre-applied cache entry for that key", () => {
        // Given: a filter is added to the tree and marked as pre-applied
        const {tree, preApplied, actions} = setup()
        const filter = makeFilter()
        actions.addFilter(filter)
        preApplied.markAsPreApplied([filter])
        expect(preApplied.hasPreApplied("key")).toBe(true)

        const leaf = tree.groups.value[0]
        const filterId = leaf.kind !== "wrapper" ? leaf.filters[0]?.id : undefined
        expect(filterId).toBeDefined()

        // When: the chip is dismissed via its X, not the top-level Reset
        actions.removeFilter(filterId as string)

        // Then: the stale default no longer resurfaces on a later reset
        expect(preApplied.hasPreApplied("key")).toBe(false)
    })

    test("removeFilter leaves other keys' pre-applied entries untouched", () => {
        // Given
        const {tree, preApplied, actions} = setup()
        const keyFilter = makeFilter({id: "f1", key: "key"})
        const otherFilter = makeFilter({id: "f2", key: "other", keyLabel: "Other"})
        actions.addFilter(keyFilter)
        preApplied.markAsPreApplied([keyFilter, otherFilter])

        const leaf = tree.groups.value[0]
        const filterId = leaf.kind !== "wrapper" ? leaf.filters[0]?.id : undefined

        // When
        actions.removeFilter(filterId as string)

        // Then
        expect(preApplied.hasPreApplied("key")).toBe(false)
        expect(preApplied.hasPreApplied("other")).toBe(true)
    })
})
