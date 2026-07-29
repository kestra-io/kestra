/**
 * Orchestrator for the KsFilter feature. Wires together the dedicated sub-composables that
 * own each concern:
 *   - `useFilterGroups`        — owns the tree state
 *   - `useDismissedKeys`       — owns the dismissed-default-visible-key set
 *   - `useRouteSync`           — owns the URL ↔ tree round-trip + raw-editor surface
 *   - `useFilterActions`       — owns user-facing CRUD + structural operations
 *   - `usePreAppliedFilters`   — tracks chips that arrived from the URL
 *   - `useDefaultFilter`       — applies default filters on first mount
 *
 * Pure logic lives in `utils/filterChipFactory.ts` and `utils/routeDecoder.ts`.
 *
 * The only logic that belongs in this file is what *coordinates* across sub-composables:
 * the default-filter policy derivation and the reset-to-defaults flow.
 */
import {ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {clearFilterQueryParams, keyOfComparator} from "../utils/helpers"
import {
    type AppliedFilter,
    type FilterConfiguration,
    type FilterGroup,
    type LogicalOperator,
    Comparators,
} from "../utils/filterTypes"
import {createAppliedFilter, createDefaultVisibleFilters} from "../utils/filterChipFactory"
import {newGroupId, useFilterGroups} from "./useFilterGroups"
import {useDismissedKeys} from "./useDismissedKeys"
import {usePreAppliedFilters} from "./usePreAppliedFilters"
import {applyDefaultFilters, useDefaultFilter} from "./useDefaultFilter"
import {useRouteSync} from "./useRouteSync"
import {useFilterActions} from "./useFilterActions"

export function useFilters(
    configuration: FilterConfiguration,
    showSearchInput = true,
    defaultScope?: boolean,
    defaultTimeRange?: boolean,
    defaultDuration?: string,
) {
    const router = useRouter()
    const route = useRoute()

    const tree = useFilterGroups()
    const dismissed = useDismissedKeys(configuration)
    const searchQuery = ref("")
    const preApplied = usePreAppliedFilters()

    const routeSync = useRouteSync({
        configuration,
        tree,
        dismissed,
        searchQuery,
        preApplied,
        showSearchInput,
    })

    // CRUD + structural ops — depend on routeSync.updateRoute to push to the URL.
    const actions = useFilterActions({
        tree,
        dismissed,
        searchQuery,
        updateRoute: routeSync.updateRoute,
        hasValue: routeSync.hasValue,
    })

    const timeRangeChips = configuration.keys?.filter((k) => k.valueType === "time-range") ?? []
    const timeRangeChip = timeRangeChips[0]
    const defaultFilterOptions = {
        namespace: configuration.keys?.some((k) => k.key === "namespace") ? undefined : null,
        includeScope: defaultScope ?? configuration.keys?.some((k) => k.key === "scope"),
        includeTimeRange: defaultTimeRange ?? !!timeRangeChip,
        defaultDuration,
        timeRangeFields: timeRangeChips.map((k) => k.key),
        timeRangeOperation: timeRangeChip ? keyOfComparator(timeRangeChip.comparators[0]) : undefined,
    }
    useDefaultFilter(defaultFilterOptions)

    const resetToDefaults = () => {
        dismissed.resetDismissedDefaultVisibleKeys()
        const {query: defaultQuery} = applyDefaultFilters({}, defaultFilterOptions)
        const resetFilters: AppliedFilter[] = []

        if (defaultFilterOptions.includeTimeRange && timeRangeChip) {
            const defaultKey = `filters[${timeRangeChip.key}][${defaultFilterOptions.timeRangeOperation}]`
            const defaultTimeRangeRaw = defaultQuery[defaultKey]
            const timeRangeValue = Array.isArray(defaultTimeRangeRaw) ? defaultTimeRangeRaw[0] : defaultTimeRangeRaw

            if (typeof timeRangeValue === "string" && timeRangeValue.length > 0) {
                const comparator = timeRangeChip.comparators[0] ?? Comparators.EQUALS
                resetFilters.push(
                    createAppliedFilter(timeRangeChip.key, timeRangeChip, comparator, timeRangeValue, timeRangeValue, "default"),
                )
            }
        }

        resetFilters.push(...createDefaultVisibleFilters(configuration.keys, new Set(), dismissed.dismissedKeys.value))

        const currentQuery = {...route.query}
        clearFilterQueryParams(currentQuery)
        delete currentQuery.page

        const query = {...currentQuery, ...defaultQuery}
        router.replace({query}).then(() => {
            tree.replaceTree([{id: newGroupId(), kind: "leaf", filters: resetFilters}], "OR")
        })
    }

    /** Restores a full group tree (e.g. loading a saved filter) and syncs it to the URL. */
    const replaceTree = (groups: FilterGroup[], topLogical?: LogicalOperator) => {
        tree.replaceTree(groups, topLogical)
        routeSync.updateRoute(false)
    }

    return {
        appliedFilters: tree.appliedFilters,
        groups: tree.groups,
        topLogical: tree.topLogical,
        hasDismissedDefaultVisibleKeys: dismissed.hasDismissedDefaultVisibleKeys,
        searchQuery,
        // route sync + raw editor
        hasUnrenderableFilters: routeSync.hasUnrenderableFilters,
        rawQuery: routeSync.rawQuery,
        applyRawQuery: routeSync.applyRawQuery,
        ...actions,
        replaceTree,
        resetToDefaults,
        // pre-applied filter tracking
        hasPreApplied: preApplied.hasPreApplied,
        getPreApplied: preApplied.getPreApplied,
    }
}
