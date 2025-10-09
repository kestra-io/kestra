import {ref, computed} from "vue";
import {useRoute} from "vue-router";
import {SavedFilter} from "../utils/types";
import {storageKeys} from "../../../../utils/constants";

export function useSavedFilters(prefix: string) {
    const route = useRoute();
    const savedFilters = ref<SavedFilter[]>([]);

    const routeKey = computed(() =>
        String(route.name || route.path.replace(/\//g, "_").replace(/^_/, ""))
    );

    const storageKey = computed(() =>
        `${storageKeys.SAVED_FILTERS_PREFIX}_${prefix}_${routeKey.value}`
    );

    const loadSavedFilters = () => {
        const stored = localStorage.getItem(storageKey.value);
        if (stored) {
            try {
                savedFilters.value = JSON.parse(stored);
            } catch {
                savedFilters.value = [];
            }
        } else {
            savedFilters.value = [];
        }
    };

    const saveFilter = (name: string, description: string, filters: any[]) => {
        const savedFilter: SavedFilter = {
            id: `saved_${Date.now()}`,
            name,
            description: description || undefined,
            filters: [...filters],
            createdAt: new Date()
        };

        const existingFilters = JSON.parse(localStorage.getItem(storageKey.value) || "[]");
        existingFilters.push(savedFilter);
        localStorage.setItem(storageKey.value, JSON.stringify(existingFilters));

        loadSavedFilters();
    };

    const updateSavedFilter = (id: string, name: string, description: string) => {
        const existingFilters = JSON.parse(localStorage.getItem(storageKey.value) || "[]");
        const filterIndex = existingFilters.findIndex((f: SavedFilter) => f.id === id);

        if (filterIndex !== -1) {
            existingFilters[filterIndex].name = name;
            existingFilters[filterIndex].description = description;
            localStorage.setItem(storageKey.value, JSON.stringify(existingFilters));
            loadSavedFilters();
        }
    };

    const deleteSavedFilter = (savedFilter: SavedFilter) => {
        const existingFilters = JSON.parse(localStorage.getItem(storageKey.value) || "[]");
        const filteredFilters = existingFilters.filter((f: SavedFilter) => f.id !== savedFilter.id);
        localStorage.setItem(storageKey.value, JSON.stringify(filteredFilters));

        loadSavedFilters();
    };

    return {
        savedFilters: computed(() => savedFilters.value),
        loadSavedFilters,
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter
    };
}