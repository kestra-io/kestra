import {ref, watch, computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import {keyOfComparator} from "../utils/helpers";
import {AppliedFilter, FilterConfiguration, COMPARATOR_LABELS, Comparators} from "../utils/filterTypes";
import {
    decodeSearchParams,
    encodeFiltersToQuery,
    isValidFilter,
    getUniqueFilters,
    clearFilterQueryParams
} from "../utils/helpers";

export function useFilters(configuration: FilterConfiguration, showSearchInput = true, legacyQuery = false) {
    const router = useRouter();
    const route = useRoute();

    const appliedFilters = ref<AppliedFilter[]>([]);
    const preAppliedFilterKeys = ref<Set<string>>(new Set());
    const searchQuery = ref("");

    /**
     * Appends value to query param, handling arrays.
     * @param query - Query object to modify
     * @param key - Query parameter key
     * @param value - Value to append
     */
    const appendQueryParam = (query: Record<string, any>, key: string, value: string) => {
        if (query[key]) {
            if (Array.isArray(query[key])) {
                query[key].push(value);
            } else {
                query[key] = [query[key], value];
            }
        } else {
            query[key] = value;
        }
    };

    /**
     * Checks if filter is a time range filter.
     * @param filter - Filter to check
     * @returns True if time range filter
     */
    const isTimeRange = (filter: AppliedFilter) =>
        typeof filter.value === "object" && "startDate" in filter.value && filter.key === "timeRange";

    /**
     * Updates search query in URL query object.
     * @param query - Query object to update
     */
    const updateSearchQuery = (query: Record<string, any>) => {
        const trimmedQuery = searchQuery.value?.trim();
        if (!trimmedQuery || !showSearchInput) {
            delete query.q;
            delete query.search;
            delete query["filters[q][EQUALS]"];
            return;
        }
        const searchKey = configuration.keys?.length > 0 && !legacyQuery
            ? "filters[q][EQUALS]"
            : "q";
        query[searchKey] = trimmedQuery;
    };

    /**
     * Clears legacy query parameters from query object.
     * @param query - Query object to clean
     */
    const clearLegacyParams = (query: Record<string, any>) => {
        configuration.keys?.forEach(({key}) => {
            delete query[key];
            if (key === "details") {
                Object.keys(query).forEach(queryKey => {
                    if (queryKey.startsWith("details.")) {
                        delete query[queryKey];
                    }
                });
            }
        });
        delete query.startDate;
        delete query.endDate;
    };

    /**
     * Builds legacy query parameters from applied filters.
     * @param query - Query object to populate
     */
    const buildLegacyQuery = (query: Record<string, any>) => {
        getUniqueFilters(appliedFilters.value.filter(isValidFilter)).forEach(filter => {
            if (filter.key === "details") {  // AuditLogs Details
                (filter.value as string[]).forEach(item => {
                    const [k, v] = item.split(":");
                    query[`details.${k}`] = v;
                });
            } else if (Array.isArray(filter.value)) {
                filter.value.forEach(item => appendQueryParam(query, filter.key, item?.toString() || ""));
            } else if (isTimeRange(filter)) {
                const {startDate, endDate} = filter.value as { startDate: Date; endDate: Date };
                query.startDate = startDate.toISOString();
                query.endDate = endDate.toISOString();
            } else {
                query[filter.key] = filter.value?.toString() || "";
            }
        });
    };

    /**
     * Updates route with current filter state.
     */
    const updateRoute = () => {
        const query = {...route.query};
        clearFilterQueryParams(query);

        if (legacyQuery) {
            clearLegacyParams(query);
            buildLegacyQuery(query);
        } else {
            Object.assign(query, encodeFiltersToQuery(getUniqueFilters(appliedFilters.value
                .filter(isValidFilter)), keyOfComparator));
        }

        updateSearchQuery(query);
        router.push({query});
    };

    /**
     * Creates AppliedFilter object.
     * @param key - Filter key
     * @param config - Filter configuration
     * @param comparator - Comparison operator
     * @param value - Filter value
     * @param valueLabel - Display label for value
     * @param idSuffix - Suffix for unique ID
     * @returns AppliedFilter object
     */
    const createAppliedFilter = (
        key: string,
        config: any,
        comparator: Comparators,
        value: any,
        valueLabel: string,
        idSuffix: string
    ): AppliedFilter => ({
        id: `${key}-${idSuffix}-${Date.now()}`,
        key,
        keyLabel: config?.label,
        comparator,
        comparatorLabel: COMPARATOR_LABELS[comparator],
        value,
        valueLabel
    });

    /**
     * Creates standard filter object.
     * @param key - Filter key
     * @param config - Filter configuration
     * @param value - Filter value(s)
     * @returns AppliedFilter object
     */
    const createFilter = (key: string, config: any, value: string | string[]): AppliedFilter => {
        const comparator = (config?.comparators?.[0] as Comparators) ?? Comparators.EQUALS;
        const valueLabel = Array.isArray(value)
            ? key === "details"
                ? value.length > 1 ? `${value[0]} +${value.length - 1}` : value[0]
                : value.join(", ")
            : (value as string);
        return createAppliedFilter(key, config, comparator, value, valueLabel, "EQUALS");
    }

    /**
     * Creates time range filter object.
     * @param config - Filter configuration
     * @param startDate - Start date
     * @param endDate - End date
     * @param comparator - Comparison operator
     * @returns Time range AppliedFilter object
     */
    const createTimeRangeFilter = (
        config: any,
        startDate: Date,
        endDate: Date,
        comparator = Comparators.EQUALS
    ): AppliedFilter => {
        const valueLabel = `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`;
        const filter = createAppliedFilter(
            "timeRange",
            config,
            comparator,
            {startDate, endDate},
            valueLabel,
            keyOfComparator(comparator)
        );
        filter.comparatorLabel = "Is Between";
        return filter;
    };

    /**
     * Parses filters from legacy URL parameters.
     * @returns Array of AppliedFilter objects
     */
    const parseLegacyFilters = (): AppliedFilter[] => {
        const filtersMap = new Map<string, AppliedFilter>();
        const details: string[] = [];

        Object.entries(route.query).forEach(([key, value]) => {
            if (["q", "search", "filters[q][EQUALS]"].includes(key)) return;
            if (key.startsWith("details.")) {
                const detailKey = key.split(".")[1];
                details.push(`${detailKey}:${value}`);
                return;
            }
            const config = configuration.keys?.find(k => k.key === key);
            if (!config) return;

            const processedValue = Array.isArray(value)
                ? (value as string[]).filter(v => v !== null)
                : config?.valueType === "multi-select"
                    ? ((value as string) ?? "").split(",")
                    : (value as string) ?? "";

            filtersMap.set(key, createFilter(key, config, processedValue));
        });

        if (details.length > 0) {
            const config = configuration.keys?.find(k => k.key === "details");
            if (config) {
                filtersMap.set("details", createFilter("details", config, details));
            }
        }

        if (route.query.startDate && route.query.endDate) {
            const timeRangeConfig = configuration.keys?.find(k => k.key === "timeRange");
            if (timeRangeConfig) {
                const startDate = new Date(route.query.startDate as string);
                const endDate = new Date(route.query.endDate as string);
                filtersMap.set(
                    "timeRange",
                    createTimeRangeFilter(timeRangeConfig, startDate, endDate)
                );
            }
        }

        return Array.from(filtersMap.values());
    };

    const TEXT_COMPARATORS = [
        Comparators.STARTS_WITH,
        Comparators.ENDS_WITH,
        Comparators.CONTAINS
    ];

    /**
     * Processes field values for filters.
     * @param config - Filter configuration
     * @param params - Parameter objects array
     * @param field - Field name
     * @param comparator - Comparison operator
     * @returns Processed value and label
     */
    const processFieldValue = (config: any, params: any[], field: string, comparator: Comparators) => {
        const filterTextComparator = TEXT_COMPARATORS.includes(comparator);

        if (config?.valueType === "multi-select" && !filterTextComparator) {
            const combinedValue = field === "labels"
                ? params.map(p => p?.value as string)
                : params.flatMap(p =>
                    Array.isArray(p?.value) ? p.value : (p?.value as string)?.split(",") ?? []
                );
            return {value: combinedValue, valueLabel: combinedValue.join(", ")};
        } else {
            const param = params[0];
            const value = Array.isArray(param?.value) ? param.value[0] : param?.value as string;
            return {value, valueLabel: value};
        }
    };

    /**
     * Checks if date filters contain valid time range.
     * @param dateFilters - Date filter objects
     * @returns True if both dates present
     */
    const hasValidTimeRange = (dateFilters: Record<string, any>) =>
        dateFilters.startDate && dateFilters.endDate;

    /**
     * Parses filters from encoded URL parameters.
     * @returns Array of AppliedFilter objects
     */
    const parseEncodedFilters = (): AppliedFilter[] => {
        const filtersMap = new Map<string, AppliedFilter>();
        const dateFilters: Record<string, {comparatorKey: string; value: string}> = {};
        const fieldParams = new Map<string, any[]>();

        decodeSearchParams(route.query).forEach(param => {
            if (!param) return;
            if (["startDate", "endDate"].includes(param?.field)) {
                dateFilters[param.field] = {
                    comparatorKey: param?.operation ?? "",
                    value: param?.value as string
                };
            } else {
                if (!fieldParams.has(param?.field)) fieldParams.set(param.field, []);
                fieldParams.get(param?.field)!.push(param);
            }
        });

        fieldParams.forEach((params, field) => {
            const config = configuration.keys?.find(k => k?.key === field);
            if (!config) return;

            const comparator = Comparators[params[0]?.operation as keyof typeof Comparators];
            if (!comparator) return;

            const {value, valueLabel} = processFieldValue(config, params, field, comparator);
            filtersMap.set(
                field,
                createAppliedFilter(field, config, comparator, value, valueLabel, params[0]?.operation)
            );
        });

        if (hasValidTimeRange(dateFilters)) {
            const timeRangeConfig = configuration.keys?.find(k => k?.key === "timeRange");
            if (timeRangeConfig) {
                const comparator = Comparators[
                    dateFilters.startDate?.comparatorKey as keyof typeof Comparators
                ];
                const startDate = new Date(dateFilters.startDate?.value);
                const endDate = new Date(dateFilters.endDate?.value);
                filtersMap.set(
                    "timeRange",
                    createTimeRangeFilter(timeRangeConfig, startDate, endDate, comparator)
                );
            }
        }

        return Array.from(filtersMap.values());
    };

    /**
     * Initializes filter state from route query parameters.
     */
    const initializeFromRoute = () => {
        if (showSearchInput) {
            searchQuery.value =
                (route.query?.["filters[q][EQUALS]"] as string) ??
                (route.query?.q as string) ??
                "";
        }
        const parsedFilters = legacyQuery
            ? parseLegacyFilters()
            : parseEncodedFilters();

        if (appliedFilters.value?.length === 0 && parsedFilters.length > 0) {
            parsedFilters.forEach(filter => preAppliedFilterKeys.value?.add(filter.key));
        }

        appliedFilters.value = parsedFilters;
    };

    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false});
    initializeFromRoute();

    /**
     * Adds filter to applied filters list.
     * @param filter - Filter to add
     */
    const addFilter = (filter: AppliedFilter) => {
        const index = appliedFilters.value.findIndex(f => f?.key === filter?.key);
        if (index === -1) {
            appliedFilters.value.push(filter);
        } else {
            appliedFilters.value[index] = filter;
        }
        updateRoute();
    };

    /**
     * Removes filter by ID.
     * @param filterId - ID of filter to remove
     */
    const removeFilter = (filterId: string) => {
        const filter = appliedFilters.value.find(f => f?.id === filterId);
        if (filter) {
            appliedFilters.value = appliedFilters.value.filter(f => f?.key !== filter?.key);
        }
        updateRoute();
    };

    /**
     * Updates existing filter.
     * @param updatedFilter - Updated filter object
     */
    const updateFilter = (updatedFilter: AppliedFilter) => {
        appliedFilters.value = [...appliedFilters.value.filter(f => f?.key !== updatedFilter?.key), updatedFilter];
        updateRoute();
    };

    /**
     * Clears all applied filters and search query.
     */
    const clearFilters = () => {
        appliedFilters.value = [];
        searchQuery.value = "";
        updateRoute();
    };

    /**
     * Resets user-applied filters while preserving pre-applied filters.
     */
    const resetToPreApplied = () => {
        appliedFilters.value = appliedFilters.value?.filter(f => preAppliedFilterKeys.value?.has(f.key));
        searchQuery.value = "";
        updateRoute();
    };

    return {
        appliedFilters: computed(() => appliedFilters.value),
        searchQuery: computed({
            get: () => searchQuery.value,
            set: value => {
                searchQuery.value = value;
                updateRoute();
            }
        }),
        addFilter,
        removeFilter,
        updateFilter,
        clearFilters,
        resetToPreApplied,
    };
}
