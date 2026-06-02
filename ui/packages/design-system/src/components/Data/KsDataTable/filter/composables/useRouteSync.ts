/**
 * Owns the URL ↔ tree round-trip for `useFilters`. Reads chips from the route on mount and
 * whenever the URL changes; writes chips back to the route when the tree, top operator, or
 * search query mutates. Also exposes the raw-editor escape hatch for queries the chip UI
 * can't render.
 *
 * Takes shared state as parameters so it can stay a standalone composable instead of a
 * tangled `useFilters` section — `tree` and `dismissed` are owned by the caller.
 */
import {computed, watch, type Ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {
    clearFilterQueryParams,
    encodeFilterGroupsToQuery,
    findUnrenderableFilterKeys,
    getUniqueFilters,
    isValidFilter,
    keyOfComparator,
    parseFiltersFromString,
    serializeFiltersToString,
} from "../utils/helpers"
import {SEARCH_QUERY_KEY} from "../utils/constants"
import {
    type AppliedFilter,
    type FilterConfiguration,
    type FilterGroup,
    type LeafFilterGroup,
    isLeafGroup,
    isWrapperGroup,
} from "../utils/filterTypes"
import {createDefaultVisibleFilters} from "../utils/filterChipFactory"
import {parseEncodedGroups} from "../utils/routeDecoder"
import {allFilters, newGroupId, type useFilterGroups} from "./useFilterGroups"
import type {useDismissedKeys} from "./useDismissedKeys"
import type {usePreAppliedFilters} from "./usePreAppliedFilters"

type Tree = ReturnType<typeof useFilterGroups>
type Dismissed = ReturnType<typeof useDismissedKeys>
type PreApplied = ReturnType<typeof usePreAppliedFilters>

interface UseRouteSyncOptions {
    configuration: FilterConfiguration;
    tree: Tree;
    dismissed: Dismissed;
    searchQuery: Ref<string>;
    preApplied: PreApplied;
    showSearchInput: boolean;
}

export function useRouteSync({
    configuration,
    tree,
    dismissed,
    searchQuery,
    preApplied,
    showSearchInput,
}: UseRouteSyncOptions) {
    const router = useRouter()
    const route = useRoute()

    /** True if the filter has a non-empty value (ignores empty arrays/strings/null). */
    const hasValue = (filter: AppliedFilter): boolean =>
        (Array.isArray(filter.value) && filter.value.length > 0)
        || (!Array.isArray(filter.value) && filter.value !== "" && filter.value != null)

    /** Strip invalid values and dedupe by (key, comparator) within each leaf group. */
    const sanitizeLeaf = (leaf: LeafFilterGroup): LeafFilterGroup =>
        ({...leaf, filters: getUniqueFilters(leaf.filters.filter(isValidFilter))})

    const updateSearchQueryParam = (query: Record<string, any>) => {
        const trimmed = searchQuery.value?.trim()
        delete query.q
        delete query.search
        delete query[SEARCH_QUERY_KEY]
        if (trimmed && showSearchInput) query[SEARCH_QUERY_KEY] = trimmed
    }

    /** Sanitize tree → encode → push to the router. The single point that writes to the URL. */
    const updateRoute = (shouldResetPage = false) => {
        const query = {...route.query}
        clearFilterQueryParams(query)

        const validUnits: FilterGroup[] = tree.groups.value.flatMap((unit): FilterGroup[] => {
            if (isWrapperGroup(unit)) {
                const cleanedChildren = unit.children.map(sanitizeLeaf).filter(c => c.filters.length > 0)
                if (cleanedChildren.length === 0) return []
                if (cleanedChildren.length === 1) return [cleanedChildren[0]] // unwrap single-child wrapper
                return [{...unit, children: cleanedChildren}]
            }
            const cleaned = sanitizeLeaf(unit)
            return cleaned.filters.length > 0 ? [cleaned] : []
        })

        Object.assign(query, encodeFilterGroupsToQuery(validUnits, keyOfComparator, tree.topLogical.value))
        updateSearchQueryParam(query)

        if (shouldResetPage && parseInt(String(query.page ?? "1")) > 1) delete query.page

        router.push({query})
    }

    /**
     * Place default-visible chips alongside the user's primary filters in the first LEAF
     * group. If the first top unit is a wrapper, prepend a fresh leaf for the defaults.
     */
    const mergeDefaultVisibleIntoFirstLeaf = (
        parsedGroups: FilterGroup[],
        defaultsForFirstGroup: AppliedFilter[],
    ): FilterGroup[] => {
        const head = parsedGroups[0]
        if (parsedGroups.length === 0) {
            return defaultsForFirstGroup.length > 0
                ? [{id: newGroupId(), kind: "leaf", filters: defaultsForFirstGroup}]
                : []
        }
        if (parsedGroups.length === 1 && isLeafGroup(head) && head.filters.length === 0) {
            return [{...head, filters: defaultsForFirstGroup}]
        }
        if (isLeafGroup(head)) {
            return [
                {...head, filters: [...head.filters, ...defaultsForFirstGroup]},
                ...parsedGroups.slice(1),
            ]
        }
        if (defaultsForFirstGroup.length > 0) {
            return [
                {id: newGroupId(), kind: "leaf", filters: defaultsForFirstGroup},
                ...parsedGroups,
            ]
        }
        return parsedGroups
    }

    const initializeFromRoute = () => {
        if (showSearchInput) {
            searchQuery.value = (route.query?.[SEARCH_QUERY_KEY] as string) ?? ""
        }

        const {groups: parsedGroups, topLogical: parsedTop} = parseEncodedGroups(route.query, configuration)
        const parsedFlat = allFilters(parsedGroups)

        if (tree.appliedFilters.value?.length === 0 && parsedFlat.length > 0) {
            preApplied.markAsPreApplied(parsedFlat)
        }

        // Restore any dismissed-key entries that are now back in the URL.
        const parsedFilterKeys = new Set(parsedFlat.map(f => f.key))
        parsedFilterKeys.forEach(k => dismissed.restoreDefaultVisibleKey(k))

        const defaultsForFirstGroup = createDefaultVisibleFilters(
            configuration.keys,
            parsedFilterKeys,
            dismissed.dismissedKeys.value,
        )
        const finalGroups = mergeDefaultVisibleIntoFirstLeaf(parsedGroups, defaultsForFirstGroup)
        tree.replaceTree(finalGroups, parsedTop)
    }

    // Watch the URL and the search query; both flow back into the tree / out to the route.
    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false})
    watch(searchQuery, () => updateRoute(searchQuery.value.trim() !== ""))
    initializeFromRoute()

    // -----------------------------------------------------------------------------
    // Raw-editor escape hatch (URL is too complex for the chip UI)
    // -----------------------------------------------------------------------------

    /**
     * True when the URL contains `filters[...]` keys the chip UI can't render — i.e. anything
     * beyond a top-level group plus one wrapper level (more than two `[and|or][N]` segments).
     */
    const hasUnrenderableFilters = computed(() => findUnrenderableFilterKeys(route.query).length > 0)

    /** Serialised raw view of every `filters[...]` query param. The raw editor consumes this string. */
    const rawQuery = computed(() => serializeFiltersToString(route.query))

    /** Replace every `filters[...]` route param with the parsed contents of the editor string. */
    const applyRawQuery = (str: string) => {
        const newFilters = parseFiltersFromString(str)
        const query = {...route.query}
        clearFilterQueryParams(query)
        Object.assign(query, newFilters)
        delete query.page
        router.push({query})
    }

    return {
        hasValue,
        updateRoute,
        hasUnrenderableFilters,
        rawQuery,
        applyRawQuery,
    }
}
