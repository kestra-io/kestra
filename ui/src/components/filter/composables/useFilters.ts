import {ref, watch, computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import {
    keyOfComparator,
    decodeSearchParams,
    encodeFiltersToQuery,
    isValidFilter,
    getUniqueFilters,
    clearFilterQueryParams
} from "../utils/helpers";
import {
    AppliedFilter,
    FilterConfiguration,
    COMPARATOR_LABELS,
    Comparators,
    TEXT_COMPARATORS,
} from "../utils/filterTypes";
import {usePreAppliedFilters} from "./usePreAppliedFilters";
import {useDefaultFilter} from "./useDefaultFilter";


export function useFilters(
    configuration: FilterConfiguration, 
    showSearchInput = true, 
    legacyQuery = false, 
    defaultScope?: boolean, 
    defaultTimeRange?: boolean
) {
    const router = useRouter();
    const route = useRoute();

    const appliedFilters = ref<AppliedFilter[]>([]);
    const searchQuery = ref("");

    const {
        markAsPreApplied,
        hasPreApplied,
        getPreApplied
    } = usePreAppliedFilters();

    const appendQueryParam = (query: Record<string, any>, key: string, value: string) => {
        if (query[key]) {
            query[key] = Array.isArray(query[key]) ? [...query[key], value] : [query[key], value];
        } else {
            query[key] = value;
        }
    };

    const isTimeRange = (filter: AppliedFilter) =>
        typeof filter.value === "object" &&
        "startDate" in filter.value &&
        filter.key === "timeRange";

    const updateSearchQuery = (query: Record<string, any>) => {
        const trimmedQuery = searchQuery.value?.trim();
        delete query.q;
        delete query.search;
        delete query["filters[q][EQUALS]"];
        
        if (trimmedQuery && showSearchInput) {
            const searchKey = configuration.keys?.length > 0 && !legacyQuery
                ? "filters[q][EQUALS]"
                : "q";
            query[searchKey] = trimmedQuery;
        }
    };

    const clearLegacyParams = (query: Record<string, any>) => {
        configuration.keys?.forEach(({key, valueType}) => {
            delete query[key];
            if (valueType === "key-value") {
                Object.keys(query).forEach(queryKey => {
                    if (queryKey.startsWith(`${key}.`)) delete query[queryKey];
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
            if (configuration.keys?.find(k => k.key === filter.key)?.valueType === "key-value") {
                (filter.value as string[]).forEach(item => {
                    const [k, v] = item.split(":");
                    query[`${filter.key}.${k}`] = v;
                });
            } else if (Array.isArray(filter.value)) {
                filter.value.forEach(item =>
                    appendQueryParam(query, filter.key, item?.toString() ?? "")
                );
            } else if (isTimeRange(filter)) {
                const {startDate, endDate} = filter.value as { startDate: Date; endDate: Date };
                query.startDate = startDate.toISOString();
                query.endDate = endDate.toISOString();
            } else {
                query[filter.key] = filter.value?.toString() || "";
            }
        });
    };

    const hasValue = (filter: AppliedFilter): boolean => {
        return (Array.isArray(filter.value) && filter.value.length > 0) ||
            (!Array.isArray(filter.value) && filter.value !== "" && filter.value !== null && filter.value !== undefined);
    };

    const updateRoute = (shouldResetPage = false) => {
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

        if (shouldResetPage && parseInt(String(query.page ?? "1")) > 1) {
            delete query.page;
        }

        router.push({query});
    };

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

    const createFilter = (
        key: string,
        config: any,
        value: string | string[]
    ): AppliedFilter => {
        const comparator = (config?.comparators?.[0] as Comparators) ?? Comparators.EQUALS;
        return createAppliedFilter(key, config, comparator, value, 
            config?.valueType === "key-value" && Array.isArray(value)
                ? value.length > 1 ? `${value[0]} +${value.length - 1}` : value[0] ?? ""
                : Array.isArray(value)
                    ? value.join(", ")
                    : value as string
        , "EQUALS");
    };

    const createTimeRangeFilter = (
        config: any,
        startDate: Date,
        endDate: Date,
        comparator = Comparators.EQUALS
    ): AppliedFilter => {
        return {
            ...createAppliedFilter(
                "timeRange",
                config,
                comparator,
                {startDate, endDate},
                `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`,
                keyOfComparator(comparator)
            ),
            comparatorLabel: "Is Between"
        };
    };

    /**
     * Parses filters from legacy URL parameters.
     * @returns Array of AppliedFilter objects
     */
    const parseLegacyFilters = (): AppliedFilter[] => {
        const filtersMap = new Map<string, AppliedFilter>();
        const keyValueFilters: Record<string, string[]> = {};

        Object.entries(route.query).forEach(([key, value]) => {
            if (["q", "search", "filters[q][EQUALS]"].includes(key)) return;

            const kvConfig = configuration.keys?.find(k => key.startsWith(`${k.key}.`) && k.valueType === "key-value");
            if (kvConfig) {
                if (!keyValueFilters[kvConfig.key]) keyValueFilters[kvConfig.key] = [];
                keyValueFilters[kvConfig.key].push(`${key.split(".")[1]}:${value}`);
                return;
            }

            const config = configuration.keys?.find(k => k.key === key);
            if (!config) return;

            filtersMap.set(key, createFilter(key, config, 
                Array.isArray(value)
                    ? (value as string[]).filter(v => v !== null)
                    : config?.valueType === "multi-select"
                        ? ((value as string) ?? "").split(",")
                        : ((value as string) ?? "")
            ));
        });

        Object.entries(keyValueFilters).forEach(([key, values]) => {
            const config = configuration.keys?.find(k => k.key === key);
            if (config) {
                filtersMap.set(key, createFilter(key, config, values));
            }
        });

        if (route.query.startDate && route.query.endDate) {
            const timeRangeConfig = configuration.keys?.find(k => k.key === "timeRange");
            if (timeRangeConfig) {
                filtersMap.set(
                    "timeRange",
                    createTimeRangeFilter(
                        timeRangeConfig,
                        new Date(route.query.startDate as string),
                        new Date(route.query.endDate as string)
                    )
                );
            }
        }

        return Array.from(filtersMap.values());
    };

    const processFieldValue = (config: any, params: any[], _field: string, comparator: Comparators) => {
        const isTextOp = TEXT_COMPARATORS.includes(comparator);

        if (config?.valueType === "key-value") {
            const combinedValue = params.map(p => p?.value as string);
            return {
                value: combinedValue,
                valueLabel: combinedValue.length > 1
                    ? `${combinedValue[0]} +${combinedValue.length - 1}`
                    : combinedValue[0] ?? ""
            };
        }

        if (config?.valueType === "multi-select" && !isTextOp) {
            const combinedValue = params.flatMap(p =>
                Array.isArray(p?.value) ? p.value : (p?.value as string)?.split(",") ?? []
            );
            return {
                value: combinedValue,
                valueLabel: combinedValue.join(", ")
            };
        }

        let value = Array.isArray(params[0]?.value)
            ? params[0].value[0]
            : (params[0]?.value as string);

        if (config?.valueType === "date" && typeof value === "string") {
            value = new Date(value);
        }

        return {
            value,
            valueLabel: value instanceof Date ? value.toLocaleDateString() : value
        };
    };

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

        if (dateFilters.startDate && dateFilters.endDate) {
            const timeRangeConfig = configuration.keys?.find(k => k?.key === "timeRange");
            if (timeRangeConfig) {
                const comparator = Comparators[
                    dateFilters.startDate?.comparatorKey as keyof typeof Comparators
                ];
                filtersMap.set(
                    "timeRange",
                    createTimeRangeFilter(
                        timeRangeConfig,
                        new Date(dateFilters.startDate?.value),
                        new Date(dateFilters.endDate?.value),
                        comparator
                    )
                );
            }
        }

        return Array.from(filtersMap.values());
    };


        /**
        * Initialize default visible filters. These filters are marked with visibleByDefault: true
        * and are automatically added to the filter list when the page loads, even if no value
        * are present to filter. Users can remove them, but they will reappear on page refresh.
        */

    const createDefaultVisibleFilters = (excludedKeys = new Set<string>()) =>
        configuration.keys
            ?.filter(key => key.visibleByDefault && !excludedKeys.has(key.key))
            .map(key => {
                const comparator = (key.comparators?.[0] as Comparators) ?? Comparators.EQUALS;
                const value = key.valueType === "multi-select" ? [] : "";
                const valueLabel = "";
                return {
                    ...createAppliedFilter(key.key, key, comparator, value, valueLabel, "default"),
                    isDefaultVisible: true
                } as AppliedFilter;
            }) ?? [];

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
            markAsPreApplied(parsedFilters);
        }

        const parsedFilterKeys = new Set(parsedFilters.map(f => f.key));
        appliedFilters.value = [...parsedFilters, ...createDefaultVisibleFilters(parsedFilterKeys)];
    };

    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false});
    initializeFromRoute();

    const addFilter = (filter: AppliedFilter) => {
        const index = appliedFilters.value.findIndex(f => f?.key === filter?.key);
        appliedFilters.value = index === -1
            ? [...appliedFilters.value, filter]
            : appliedFilters.value.map((f, i) => (i === index ? filter : f));
        updateRoute(hasValue(filter));
    };

    const removeFilter = (filterId: string) => {
        const filter = appliedFilters.value.find(f => f?.id === filterId);
        if (filter) {
            appliedFilters.value = appliedFilters.value.filter(f => f?.key !== filter?.key);
            updateRoute(false);
        }
    };

    const updateFilter = (updatedFilter: AppliedFilter) => {
        appliedFilters.value = [
            ...appliedFilters.value.filter(f => f?.key !== updatedFilter?.key),
            updatedFilter
        ];
        updateRoute(hasValue(updatedFilter));
    };

    /**
     * Clears all applied filters and search query.
     */
    const clearFilters = () => {
        appliedFilters.value = [];
        searchQuery.value = "";
        updateRoute(true);
    };

    const {resetDefaultFilter} = useDefaultFilter({
        legacyQuery,
        namespace: configuration.keys?.some((k) => k.key === "namespace") ? undefined : null,
        includeScope: defaultScope ?? configuration.keys?.some((k) => k.key === "scope"),
        includeTimeRange: defaultTimeRange ?? configuration.keys?.some((k) => k.key === "timeRange"),
    });

    const resetToPreApplied = () => {
        searchQuery.value = "";

        const parsedFilters = legacyQuery ? parseLegacyFilters() : parseEncodedFilters();

        const parsedFilterKeys = new Set(parsedFilters.map((f: AppliedFilter) => f.key));
        appliedFilters.value = [...parsedFilters, ...createDefaultVisibleFilters(parsedFilterKeys)];

        resetDefaultFilter();
    };
    
    watch(searchQuery, () => {
        updateRoute(searchQuery.value.trim() !== "");
    });

    return {
        appliedFilters: computed(() => appliedFilters.value),
        searchQuery,
        addFilter,
        removeFilter,
        updateFilter,
        clearFilters,
        resetToPreApplied,
        hasPreApplied,
        getPreApplied,
    };
}
