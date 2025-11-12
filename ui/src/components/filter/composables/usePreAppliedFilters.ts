import {ref} from "vue";
import type {AppliedFilter} from "../utils/filterTypes";

export function usePreAppliedFilters() {
    const preAppliedKeys = ref<Set<string>>(new Set());
    const preAppliedDefaults = ref<Map<string, AppliedFilter>>(new Map());

    const markAsPreApplied = (filters: AppliedFilter[]) => {
        filters.forEach(filter => {
            preAppliedKeys.value.add(filter.key);
            preAppliedDefaults.value.set(filter.key, {...filter});
        });
    };

    const hasPreApplied = (filterKey: string): boolean => {
        return preAppliedDefaults.value.has(filterKey);
    };

    const getPreApplied = (filterKey: string): AppliedFilter | undefined => {
        return preAppliedDefaults.value.get(filterKey);
    };

    const isPreApplied = (filterKey: string): boolean => {
        return preAppliedKeys.value.has(filterKey);
    };

    const getAllPreApplied = (): AppliedFilter[] => {
        return Array.from(preAppliedDefaults.value.values());
    };

    return {
        markAsPreApplied,
        hasPreApplied,
        getPreApplied,
        isPreApplied,
        getAllPreApplied
    };
}
