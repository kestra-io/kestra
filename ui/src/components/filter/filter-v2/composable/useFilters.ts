import {ref, watch, computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import {AppliedFilter, FilterConfiguration, COMPARATOR_LABELS} from "../utils/types";
import {Comparators, keyOfComparator} from "../../../../composables/monaco/languages/filters/filterCompletion";
import {decodeSearchParams, encodeFiltersToQuery, isValidFilter, getUniqueFilters, clearFilterQueryParams} from "../../utils/helpers";

export function useFilters(configuration: FilterConfiguration, _prefix = "", showSearchInput = true) {
    const router = useRouter();
    const route = useRoute();

    const appliedFilters = ref<AppliedFilter[]>([]);
    const searchQuery = ref("");

    const updateRoute = () => {
        const query = {...route.query};

        clearFilterQueryParams(query);

        const validFilters = appliedFilters.value.filter(isValidFilter);
        const uniqueFilters = getUniqueFilters(validFilters);

        const encodedQuery = encodeFiltersToQuery(uniqueFilters, keyOfComparator);
        Object.assign(query, encodedQuery);

        if (searchQuery.value?.trim() && showSearchInput) {
            if (configuration.keys?.length > 0) {
                query["filters[q][EQUALS]"] = searchQuery.value.trim();
            } else {
                query.q = searchQuery.value.trim();
            }
        } else {
            delete query.q;
            delete query.search;
            delete query["filters[q][EQUALS]"];
        }

        router.push({query});
    };

    const initializeFromRoute = () => {
        if (showSearchInput) {
            if (route.query["filters[q][EQUALS]"]) {
                searchQuery.value = route.query["filters[q][EQUALS]"] as string;
            } else if (route.query.q) {
                searchQuery.value = route.query.q as string;
            } else if (route.query.search) {
                searchQuery.value = route.query.search as string;
            }
        }

        const filtersMap = new Map<string, AppliedFilter>();
        const dateFilters: Record<string, {comparatorKey: string; value: string}> = {};
        const fieldParams = new Map<string, any[]>();

        decodeSearchParams(route.query).forEach(param => {
            if (!param) return;

            const {field} = param;

            if (["startDate", "endDate"].includes(field)) {
                dateFilters[field] = {comparatorKey: param.operation, value: param.value as string};
                return;
            }

            if (!fieldParams.has(field)) fieldParams.set(field, []);
            fieldParams.get(field)!.push(param);
        });

        fieldParams.forEach((params, field) => {
            const filterConfig = configuration.keys.find(key => key.key === field);
            if (!filterConfig) return;

            const comparatorEnum = Comparators[params[0].operation as keyof typeof Comparators];
            if (!comparatorEnum) return;

            let combinedValue: string | string[];
            let valueLabel: string;

            if (filterConfig.valueType === "multi-select") {
                if (field === "labels") {
                    combinedValue = params.map(p => p.value as string);
                } else {
                    combinedValue = params.flatMap(p =>
                        Array.isArray(p.value) ? p.value : (p.value as string).split(",")
                    );
                }
                valueLabel = (combinedValue as string[]).join(", ");
            } else {
                const param = params[0];
                combinedValue = Array.isArray(param.value) ? param.value[0] : param.value as string;
                valueLabel = combinedValue as string;
            }

            const filter: AppliedFilter = {
                id: `${field}-${params[0].operation}-${Date.now()}`,
                key: field,
                keyLabel: filterConfig.label || field,
                comparator: comparatorEnum,
                comparatorLabel: COMPARATOR_LABELS[comparatorEnum] || params[0].operation,
                value: combinedValue,
                valueLabel
            };

            filtersMap.set(field, filter);
        });

        if (dateFilters.startDate && dateFilters.endDate &&
            dateFilters.startDate.comparatorKey === dateFilters.endDate.comparatorKey) {

            const timeRangeConfig = configuration.keys.find(key => key.key === "timeRange");
            if (timeRangeConfig) {
                const comparatorEnum = Comparators[dateFilters.startDate.comparatorKey as keyof typeof Comparators];
                const startDate = new Date(dateFilters.startDate.value);
                const endDate = new Date(dateFilters.endDate.value);

                const filter: AppliedFilter = {
                    id: `timeRange-${dateFilters.startDate.comparatorKey}-${Date.now()}`,
                    key: "timeRange",
                    keyLabel: timeRangeConfig.label,
                    comparator: comparatorEnum,
                    comparatorLabel: COMPARATOR_LABELS[comparatorEnum] || dateFilters.startDate.comparatorKey,
                    value: {startDate, endDate},
                    valueLabel: `${startDate.toLocaleDateString()} - ${endDate.toLocaleDateString()}`
                };

                filtersMap.set("timeRange", filter);
            }
        }

        appliedFilters.value = Array.from(filtersMap.values());
    };

    watch(() => route.query, initializeFromRoute, {deep: true, immediate: false});
    initializeFromRoute();

    return {
        appliedFilters: computed(() => appliedFilters.value),
        searchQuery: computed({
            get: () => searchQuery.value,
            set: value => {
                searchQuery.value = value;
                updateRoute();
            }
        }),
        addFilter: (filter: AppliedFilter) => {
            const existingIndex = appliedFilters.value.findIndex(f => f.key === filter.key);
            if (existingIndex !== -1) appliedFilters.value[existingIndex] = filter;
            else appliedFilters.value.push(filter);
            updateRoute();
        },
        removeFilter: (filterId: string) => {
            const filterToRemove = appliedFilters.value.find(f => f.id === filterId);
            if (filterToRemove) appliedFilters.value = appliedFilters.value.filter(f => f.key !== filterToRemove.key);
            updateRoute();
        },
        updateFilter: (updatedFilter: AppliedFilter) => {
            appliedFilters.value = appliedFilters.value.filter(f => f.key !== updatedFilter.key);
            appliedFilters.value.push(updatedFilter);
            updateRoute();
        },
        clearFilters: () => {
            appliedFilters.value = [];
            searchQuery.value = "";
            updateRoute();
        }
    };
}