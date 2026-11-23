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
import {normalizeRelativeDate} from "../utils/relativeDates"
import {
    type AppliedFilter,
    type FilterConfiguration,
    type FilterKeyConfig,
    type FilterGroup,
    type LogicalOperator,
    Comparators,
} from "../utils/filterTypes"
import {createAppliedFilter, createDefaultVisibleFilters, resolveDefaultVisibleValue} from "../utils/filterChipFactory"
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
        configuration,
        tree,
        dismissed,
        preApplied,
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

    const resolveDefaultTimeRange = (): string | undefined => {
        if (!defaultFilterOptions.includeTimeRange) return undefined
        const {query} = applyDefaultFilters({}, defaultFilterOptions)
        const raw = query[`filters[${TIME_RANGE_KEY}][EQUALS]`]
        const value = Array.isArray(raw) ? raw[0] : raw
        return typeof value === "string" && value.length > 0 ? value : undefined
    }

    const getDefaultValue = (key: FilterKeyConfig): AppliedFilter["value"] => {
        const timeRange = key.key === TIME_RANGE_KEY ? resolveDefaultTimeRange() : undefined
        return timeRange ? normalizeRelativeDate(timeRange) : resolveDefaultVisibleValue(key)
    }

    const resetToDefaults = () => {
        dismissed.resetDismissedDefaultVisibleKeys()
        const {query: defaultQuery} = applyDefaultFilters({}, defaultFilterOptions)
        const resetFilters: AppliedFilter[] = []

        const timeRangeConfig = configuration.keys?.find((k) => k.key === TIME_RANGE_KEY)
        const timeRangeValue = resolveDefaultTimeRange()
        if (timeRangeConfig && timeRangeValue) {
            const comparator = timeRangeConfig.comparators[0] ?? Comparators.EQUALS
            resetFilters.push(
                createAppliedFilter(TIME_RANGE_KEY, timeRangeConfig, comparator, timeRangeValue, timeRangeValue, "default"),
            )
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
        getDefaultValue,
        // pre-applied filter tracking
        hasPreApplied: preApplied.hasPreApplied,
        getPreApplied: preApplied.getPreApplied,
    }
}
