import {computed} from "vue";
import {useRoute} from "vue-router";
import {useStorage} from "@vueuse/core";
import {SavedFilter} from "../utils/filterTypes";
import {storageKeys} from "../../../utils/constants";

const isDateString = (value: any) =>
    typeof value === "string" && !isNaN(Date.parse(value)) && value.includes("T");

const deserializeDates = (filter: SavedFilter): SavedFilter => ({
    ...filter,
    filters: filter.filters.map((f: any) => ({
        ...f,
        value: f.value?.startDate && f.value?.endDate
            ? {startDate: new Date(f.value.startDate), endDate: new Date(f.value.endDate)}
            : isDateString(f.value)
                ? new Date(f.value)
                : f.value
    })),
    createdAt: new Date(filter.createdAt)
});

export function useSavedFilters(prefix: string) {
    const route = useRoute();
    
    const storageKey = computed(() => {
        const routeKey = String(route.name || route.path.replace(/\//g, "_").replace(/^_/, ""));
        return `${storageKeys.SAVED_FILTERS_PREFIX}_${prefix}_${routeKey}`;
    });

    const savedFilters = useStorage<SavedFilter[]>(storageKey, [], localStorage, {
        serializer: {
            read: (v: string) => JSON.parse(v).map(deserializeDates),
            write: (v: SavedFilter[]) => JSON.stringify(v)
        }
    });

    const saveFilter = (name: string, description: string, filters: any[], searchQuery?: string) => {
        savedFilters.value = [...savedFilters.value, {
            id: `saved_${Date.now()}`,
            name,
            description,
            filters: [...filters],
            searchQuery,
            createdAt: new Date(),
        }];
    };

    const updateSavedFilter = (id: string, name: string, description: string) => {
        const index = savedFilters.value.findIndex((f) => f.id === id);
        if (index !== -1) {
            savedFilters.value[index] = {
                ...savedFilters.value[index],
                name,
                description
            };
        }
    };

    const deleteSavedFilter = (savedFilter: SavedFilter) => {
        savedFilters.value = savedFilters.value.filter((f) => f.id !== savedFilter.id);
    };

    return {
        savedFilters: computed(() => savedFilters.value),
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter,
    };
}