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
import {clearFilterQueryParams} from "../utils/helpers"
import {TIME_RANGE_KEY} from "../utils/constants"
import {
    type AppliedFilter,
    type FilterConfiguration,
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

    const defaultFilterOptions = {
        namespace: configuration.keys?.some((k) => k.key === "namespace") ? undefined : null,
        includeScope: defaultScope ?? configuration.keys?.some((k) => k.key === "scope"),
        includeTimeRange: defaultTimeRange ?? configuration.keys?.some((k) => k.key === "timeRange"),
        defaultDuration,
    }
    useDefaultFilter(defaultFilterOptions)

    const resetToDefaults = () => {
        dismissed.resetDismissedDefaultVisibleKeys()
        const {query: defaultQuery} = applyDefaultFilters({}, defaultFilterOptions)
        const resetFilters: AppliedFilter[] = []

        if (defaultFilterOptions.includeTimeRange) {
            const timeRangeConfig = configuration.keys?.find((k) => k.key === TIME_RANGE_KEY)
            const defaultTimeRangeRaw = defaultQuery[`filters[${TIME_RANGE_KEY}][EQUALS]`]
            const timeRangeValue = Array.isArray(defaultTimeRangeRaw) ? defaultTimeRangeRaw[0] : defaultTimeRangeRaw

            if (timeRangeConfig && typeof timeRangeValue === "string" && timeRangeValue.length > 0) {
                const comparator = timeRangeConfig.comparators[0] ?? Comparators.EQUALS
                resetFilters.push(
                    createAppliedFilter(TIME_RANGE_KEY, timeRangeConfig, comparator, timeRangeValue, timeRangeValue, "default"),
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
        resetToDefaults,
        // pre-applied filter tracking
        hasPreApplied: preApplied.hasPreApplied,
        getPreApplied: preApplied.getPreApplied,
    }
}
