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
    validStructureSignature,
} from "../utils/helpers"
import {SEARCH_QUERY_KEY} from "../utils/constants"
import {
    type AppliedFilter,
    type FilterConfiguration,
    type FilterGroup,
    type LeafFilterGroup,
    type LogicalOperator,
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

    const hasValue = (filter: AppliedFilter): boolean =>
        (Array.isArray(filter.value) && filter.value.length > 0)
        || (!Array.isArray(filter.value) && filter.value !== "" && filter.value != null)

    const sanitizeLeaf = (leaf: LeafFilterGroup): LeafFilterGroup =>
        ({...leaf, filters: getUniqueFilters(leaf.filters.filter(isValidFilter))})

    const sanitizeUnitAndUnwrapSingleChild = (unit: FilterGroup): FilterGroup[] => {
        if (isWrapperGroup(unit)) {
            const cleanedChildren = unit.children.map(sanitizeLeaf).filter((child) => child.filters.length > 0)
            if (cleanedChildren.length === 0) return []
            if (cleanedChildren.length === 1) return [cleanedChildren[0]]
            return [{...unit, children: cleanedChildren}]
        }
        const cleanedLeaf = sanitizeLeaf(unit)
        return cleanedLeaf.filters.length > 0 ? [cleanedLeaf] : []
    }

    const writeSearchQueryParam = (query: Record<string, any>) => {
        const trimmed = searchQuery.value?.trim()
        delete query.q
        delete query.search
        delete query[SEARCH_QUERY_KEY]
        if (trimmed && showSearchInput) query[SEARCH_QUERY_KEY] = trimmed
    }

    const updateRoute = (shouldResetPage = false) => {
        const query = {...route.query}
        clearFilterQueryParams(query)

        const validUnits = tree.groups.value.flatMap(sanitizeUnitAndUnwrapSingleChild)
        Object.assign(query, encodeFilterGroupsToQuery(validUnits, keyOfComparator, tree.topLogical.value))
        writeSearchQueryParam(query)

        if (shouldResetPage && parseInt(String(query.page ?? "1")) > 1) delete query.page

        router.push({query})
    }

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

    const routeAlreadyMatchesLiveTree = (parsedGroups: FilterGroup[], parsedTop: LogicalOperator): boolean =>
        tree.appliedFilters.value.length > 0
        && parsedTop === tree.topLogical.value
        && validStructureSignature(parsedGroups) === validStructureSignature(tree.groups.value)

    const initializeFromRoute = () => {
        if (showSearchInput) {
            searchQuery.value = (route.query?.[SEARCH_QUERY_KEY] as string) ?? ""
        }

        const {groups: parsedGroups, topLogical: parsedTop} = parseEncodedGroups(route.query, configuration)
        const parsedFlat = allFilters(parsedGroups)

        if (routeAlreadyMatchesLiveTree(parsedGroups, parsedTop)) return

        if (tree.appliedFilters.value?.length === 0 && parsedFlat.length > 0) {
            preApplied.markAsPreApplied(parsedFlat)
        }

        const parsedFilterKeys = new Set(parsedFlat.map((f) => f.key))
        parsedFilterKeys.forEach((key) => dismissed.restoreDefaultVisibleKey(key))

        const defaultsForFirstGroup = createDefaultVisibleFilters(
            configuration.keys,
            parsedFilterKeys,
            dismissed.dismissedKeys.value,
        )
        const finalGroups = mergeDefaultVisibleIntoFirstLeaf(parsedGroups, defaultsForFirstGroup)
        tree.replaceTree(finalGroups, parsedTop)
    }

    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false})
    watch(searchQuery, () => updateRoute(searchQuery.value.trim() !== ""))
    initializeFromRoute()

    const hasUnrenderableFilters = computed(() => findUnrenderableFilterKeys(route.query).length > 0)

    const rawQuery = computed(() => serializeFiltersToString(route.query))

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
