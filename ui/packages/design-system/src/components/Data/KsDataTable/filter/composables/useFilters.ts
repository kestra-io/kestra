import {ref, watch, computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {
    keyOfComparator,
    decodeSearchParams,
    encodeFiltersToQuery,
    isValidFilter,
    getUniqueFilters,
    clearFilterQueryParams,
} from "../utils/helpers"
import {
    type AppliedFilter,
    type FilterConfiguration,
    type FilterKeyConfig,
    COMPARATOR_LABELS,
    Comparators,
    TEXT_COMPARATORS,
} from "../utils/filterTypes"
import {usePreAppliedFilters} from "./usePreAppliedFilters"
import {applyDefaultFilters, useDefaultFilter} from "./useDefaultFilter"

export function useFilters(
    configuration: FilterConfiguration,
    showSearchInput = true,
    defaultScope?: boolean,
    defaultTimeRange?: boolean,
    defaultDuration?: string,
) {
    const router = useRouter()
    const route = useRoute()

    const appliedFilters = ref<AppliedFilter[]>([])
    const searchQuery = ref("")
    const dismissedDefaultVisibleKeys = ref<Set<string>>(new Set())

    const {
        markAsPreApplied,
        hasPreApplied,
        getPreApplied,
    } = usePreAppliedFilters()

    const isDefaultVisibleKey = (key: string) =>
        configuration.keys?.some((k) => k.key === key && k.visibleByDefault) ?? false

    const dismissDefaultVisibleKey = (key: string) => {
        if (!isDefaultVisibleKey(key)) {
            return
        }

        const next = new Set(dismissedDefaultVisibleKeys.value)
        next.add(key)
        dismissedDefaultVisibleKeys.value = next
    }

    const restoreDefaultVisibleKey = (key: string) => {
        if (!dismissedDefaultVisibleKeys.value.has(key)) {
            return
        }

        const next = new Set(dismissedDefaultVisibleKeys.value)
        next.delete(key)
        dismissedDefaultVisibleKeys.value = next
    }

    const dismissAllDefaultVisibleKeys = () => {
        dismissedDefaultVisibleKeys.value = new Set(
            configuration.keys
                ?.filter((key) => key.visibleByDefault)
                .map((key) => key.key) ?? [],
        )
    }

    const resetDismissedDefaultVisibleKeys = () => {
        dismissedDefaultVisibleKeys.value = new Set()
    }

    const hasDismissedDefaultVisibleKeys = computed(
        () => dismissedDefaultVisibleKeys.value.size > 0,
    )

    const updateSearchQuery = (query: Record<string, any>) => {
        const trimmedQuery = searchQuery.value?.trim()
        delete query.q
        delete query.search
        delete query["filters[q][EQUALS]"]

        if (trimmedQuery && showSearchInput) {
            query["filters[q][EQUALS]"] = trimmedQuery
        }
    }

    const hasValue = (filter: AppliedFilter): boolean => {
        return (Array.isArray(filter.value) && filter.value.length > 0) ||
            (!Array.isArray(filter.value) && filter.value !== "" && filter.value !== null && filter.value !== undefined)
    }

    const updateRoute = (shouldResetPage = false) => {
        const query = {...route.query}
        clearFilterQueryParams(query)

        Object.assign(query, encodeFiltersToQuery(getUniqueFilters(appliedFilters.value
            .filter(isValidFilter)), keyOfComparator))

        updateSearchQuery(query)

        if (shouldResetPage && parseInt(String(query.page ?? "1")) > 1) {
            delete query.page
        }

        router.push({query})
    }

    const createAppliedFilter = (
        key: string,
        config: any,
        comparator: Comparators,
        value: any,
        valueLabel: string,
        idSuffix: string,
        meta?: Record<string, string>,
    ): AppliedFilter => ({
        id: `${key}-${idSuffix}-${Date.now()}`,
        key,
        keyLabel: config?.keyLabelProvider ? config.keyLabelProvider(meta) : config?.label,
        comparator,
        comparatorLabel: COMPARATOR_LABELS[comparator],
        value,
        valueLabel,
        ...(meta ? {meta} : {}),
    })

    const createTimeRangeFilter = (
        config: any,
        startDate: Date,
        endDate: Date,
        comparator = Comparators.EQUALS,
        meta?: Record<string, string>,
    ): AppliedFilter => {
        return {
            ...createAppliedFilter(
                "timeRange",
                config,
                comparator,
                {startDate, endDate},
                `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`,
                keyOfComparator(comparator),
                meta,
            ),
            comparatorLabel: "Is Between",
        }
    }

    const processFieldValue = (config: any, params: any[], _field: string, comparator: Comparators) => {
        const isTextOp = TEXT_COMPARATORS.includes(comparator)

        if (config?.valueType === "key-value") {
            const combinedValue = params.map(p => p?.value as string)
            return {
                value: combinedValue,
                valueLabel: combinedValue.length > 1
                    ? `${combinedValue[0]} +${combinedValue.length - 1}`
                    : combinedValue[0] ?? "",
            }
        }

        if (config?.valueType === "multi-select" && !isTextOp) {
            const combinedValue = params.flatMap(p =>
                Array.isArray(p?.value) ? p.value : (p?.value as string)?.split(",") ?? [],
            )
            return {
                value: combinedValue,
                valueLabel: combinedValue.join(", "),
            }
        }

        let value = Array.isArray(params[0]?.value)
            ? params[0].value[0]
            : (params[0]?.value as string)

        if (config?.valueType === "date" && typeof value === "string") {
            value = new Date(value)
        }

        return {
            value,
            valueLabel: value instanceof Date ? value.toLocaleDateString() : value,
        }
    }

    const parseEncodedFilters = (): AppliedFilter[] => {
        const filtersMap = new Map<string, AppliedFilter>()
        const dateFilters: Record<string, {comparatorKey: string; value: string}> = {}
        const fieldParams = new Map<string, any[]>()

        decodeSearchParams(route.query).forEach(param => {
            if (!param) return
            if (["startDate", "endDate"].includes(param?.field)) {
                dateFilters[param.field] = {
                    comparatorKey: param?.operation ?? "",
                    value: param?.value as string,
                }
            } else {
                if (!fieldParams.has(param?.field)) fieldParams.set(param.field, [])
                fieldParams.get(param?.field)!.push(param)
            }
        })

        const routeDateFilter = route.query.dateFilter as string | undefined

        fieldParams.forEach((params, field) => {
            const config = configuration.keys?.find(k => k?.key === field)
            if (!config) return

            const parsedComparator = Comparators[params[0]?.operation as keyof typeof Comparators]
            const comparator = config.comparators?.includes(parsedComparator)
                ? parsedComparator
                : undefined
            if (!comparator) return

            const {value, valueLabel} = processFieldValue(config, params, field, comparator)
            const meta = field === "timeRange" && routeDateFilter && config.dateFilterOptions
                ? {dateFilter: routeDateFilter}
                : undefined
            filtersMap.set(
                field,
                createAppliedFilter(field, config, comparator, value, valueLabel, params[0]?.operation, meta),
            )
        })

        if (dateFilters.startDate && dateFilters.endDate) {
            const timeRangeConfig = configuration.keys?.find(k => k?.key === "timeRange")
            if (timeRangeConfig) {
                const comparator = Comparators[
                    dateFilters.startDate?.comparatorKey as keyof typeof Comparators
                ]
                const meta = routeDateFilter && timeRangeConfig.dateFilterOptions
                    ? {dateFilter: routeDateFilter}
                    : undefined
                filtersMap.set(
                    "timeRange",
                    createTimeRangeFilter(
                        timeRangeConfig,
                        new Date(dateFilters.startDate?.value),
                        new Date(dateFilters.endDate?.value),
                        comparator,
                        meta,
                    ),
                )
            }
        }

        return Array.from(filtersMap.values())
    }

        /**
        * Initialize default visible filters. These filters are marked with visibleByDefault: true
        * and are automatically added to the filter list when the page loads, even if no value
        * are present to filter. Users can remove them, but they will reappear on page refresh.
        */

    const resolveDefaultVisibleValue = (key: FilterKeyConfig): AppliedFilter["value"] => {
        const value = typeof key.defaultValue === "function"
            ? key.defaultValue()
            : key.defaultValue

        if (value !== undefined) {
            return value
        }

        return key.valueType === "multi-select" ? [] : ""
    }

    const defaultVisibleValueLabel = (value: AppliedFilter["value"]) => {
        if (Array.isArray(value)) {
            return value.join(", ")
        }

        if (value && typeof value === "object" && "startDate" in value && "endDate" in value) {
            return `${value.startDate.toLocaleDateString()} - ${value.endDate.toLocaleDateString()}`
        }

        if (value instanceof Date) {
            return value.toLocaleDateString()
        }

        return value?.toString?.() ?? ""
    }

    const createDefaultVisibleFilters = (
        excludedKeys = new Set<string>(),
        hiddenDefaultVisibleKeys = dismissedDefaultVisibleKeys.value,
    ) =>
        configuration.keys
            ?.filter(key =>
                key.visibleByDefault &&
                !excludedKeys.has(key.key) &&
                !hiddenDefaultVisibleKeys.has(key.key),
            )
            .map(key => {
                const comparator = (key.comparators?.[0] as Comparators) ?? Comparators.EQUALS
                const value = resolveDefaultVisibleValue(key)
                const valueLabel = defaultVisibleValueLabel(value)
                return {
                    ...createAppliedFilter(key.key, key, comparator, value, valueLabel, "default"),
                    isDefaultVisible: true,
                } as AppliedFilter
            }) ?? []

    const initializeFromRoute = () => {
        if (showSearchInput) {
            searchQuery.value = (route.query?.["filters[q][EQUALS]"] as string) ?? ""
        }

        const parsedFilters = parseEncodedFilters()

        if (appliedFilters.value?.length === 0 && parsedFilters.length > 0) {
            markAsPreApplied(parsedFilters)
        }

        const parsedFilterKeys = new Set(parsedFilters.map(f => f.key))
        if (parsedFilterKeys.size > 0 && dismissedDefaultVisibleKeys.value.size > 0) {
            const next = new Set(dismissedDefaultVisibleKeys.value)
            parsedFilterKeys.forEach((key) => next.delete(key))
            dismissedDefaultVisibleKeys.value = next
        }

        appliedFilters.value = [
            ...parsedFilters,
            ...createDefaultVisibleFilters(parsedFilterKeys, dismissedDefaultVisibleKeys.value),
        ]
    }

    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false})
    initializeFromRoute()

    const addFilter = (filter: AppliedFilter) => {
        restoreDefaultVisibleKey(filter.key)
        const index = appliedFilters.value.findIndex(f => f?.key === filter?.key)
        appliedFilters.value = index === -1
            ? [...appliedFilters.value, filter]
            : appliedFilters.value.map((f, i) => (i === index ? filter : f))
        updateRoute(hasValue(filter))
    }

    const removeFilter = (filterId: string) => {
        const filter = appliedFilters.value.find(f => f?.id === filterId)
        if (filter) {
            dismissDefaultVisibleKey(filter.key)
            appliedFilters.value = appliedFilters.value.filter(f => f?.key !== filter?.key)
            updateRoute(false)
        }
    }

    const updateFilter = (updatedFilter: AppliedFilter) => {
        restoreDefaultVisibleKey(updatedFilter.key)
        appliedFilters.value = [
            ...appliedFilters.value.filter(f => f?.key !== updatedFilter?.key),
            updatedFilter,
        ]
        updateRoute(hasValue(updatedFilter))
    }

    /**
     * Clears all applied filters and search query.
     */
    const clearFilters = () => {
        dismissAllDefaultVisibleKeys()
        appliedFilters.value = []
        searchQuery.value = ""
        updateRoute(true)
    }

    const defaultFilterOptions = {
        namespace: configuration.keys?.some((k) => k.key === "namespace") ? undefined : null,
        includeScope: defaultScope ?? configuration.keys?.some((k) => k.key === "scope"),
        includeTimeRange: defaultTimeRange ?? configuration.keys?.some((k) => k.key === "timeRange"),
        defaultDuration,
    }
    useDefaultFilter(defaultFilterOptions)

    const resetToDefaults = () => {
        resetDismissedDefaultVisibleKeys()

        const {query: defaultQuery} = applyDefaultFilters({}, defaultFilterOptions)
        const resetFilters: AppliedFilter[] = []

        // Append time range as first filter to preserve order
        if (defaultFilterOptions.includeTimeRange) {
            const timeRangeConfig = configuration.keys?.find((k) => k.key === "timeRange")
            const timeRangeQueryKey = "filters[timeRange][EQUALS]"
            const defaultTimeRangeForReset = defaultQuery[timeRangeQueryKey]
            const timeRangeValue = Array.isArray(defaultTimeRangeForReset) ? defaultTimeRangeForReset[0] : defaultTimeRangeForReset

            if (timeRangeConfig && typeof timeRangeValue === "string" && timeRangeValue.length > 0) {
                const comparator = (timeRangeConfig.comparators?.[0] as Comparators) ?? Comparators.EQUALS
                resetFilters.push(
                    createAppliedFilter(
                        "timeRange",
                        timeRangeConfig,
                        comparator,
                        timeRangeValue,
                        timeRangeValue,
                        "default",
                    ),
                )
            }
        }

        resetFilters.push(...createDefaultVisibleFilters(new Set(), dismissedDefaultVisibleKeys.value))

        const currentQuery = {...route.query}
        clearFilterQueryParams(currentQuery)
        delete currentQuery.page

        const query = {...currentQuery, ...defaultQuery}
        router.replace({query}).then(() => appliedFilters.value = resetFilters)
    }

    watch(searchQuery, () => {
        updateRoute(searchQuery.value.trim() !== "")
    })

    return {
        appliedFilters: computed(() => appliedFilters.value),
        hasDismissedDefaultVisibleKeys,
        searchQuery,
        addFilter,
        removeFilter,
        updateFilter,
        clearFilters,
        resetToDefaults,
        hasPreApplied,
        getPreApplied,
    }
}