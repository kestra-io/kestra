import {ref, computed} from "vue";
import {useRoute} from "vue-router";
import {SavedFilter} from "../utils/filterTypes";
import {storageKeys} from "../../../utils/constants";

export function useSavedFilters(prefix: string) {
    const route = useRoute();
    const savedFilters = ref<SavedFilter[]>([]);

    const storageKey = computed(() => {
        const routeKey = String(route.name || route.path.replace(/\//g, "_").replace(/^_/, ""));
        return `${storageKeys.SAVED_FILTERS_PREFIX}_${prefix}_${routeKey}`;
    });

    const getStoredFilters = (): SavedFilter[] => {
        const stored = localStorage.getItem(storageKey.value);
        if (stored)
            return JSON.parse(stored);
        return [];
    };

    const setStoredFilters = (filters: SavedFilter[]) => {
        localStorage.setItem(storageKey.value, JSON.stringify(filters));
        savedFilters.value = filters;
    };

    const loadSavedFilters = () => {
        savedFilters.value = getStoredFilters();
    };

    const saveFilter = (name: string, description: string, filters: any[], searchQuery?: string) => {
        const newFilter: SavedFilter = {
            id: `saved_${Date.now()}`,
            name,
            description,
            filters: [...filters],
            searchQuery,
            createdAt: new Date(),
        };
        
        const existingFilters = getStoredFilters();
        const updatedFilters = [...existingFilters, newFilter];
        setStoredFilters(updatedFilters);
    };

    const updateSavedFilter = (id: string, name: string, description: string) => {
        const existingFilters = getStoredFilters();
        const filterIndex = existingFilters.findIndex((f) => f.id === id);
        
        if (filterIndex !== -1) {
            existingFilters[filterIndex] = {
                ...existingFilters[filterIndex],
                name,
                description
            };
            setStoredFilters(existingFilters);
        }
    };

    const deleteSavedFilter = (savedFilter: SavedFilter) => {
        const existingFilters = getStoredFilters();
        const filteredFilters = existingFilters.filter((f) => f.id !== savedFilter.id);
        setStoredFilters(filteredFilters);
    };

    loadSavedFilters();

    return {
        savedFilters: computed(() => savedFilters.value),
        loadSavedFilters,
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter,
    };
}