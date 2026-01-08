import {computed} from "vue";
import {useRoute} from "vue-router";
import {useStorage} from "@vueuse/core";
import {SavedFilter} from "../utils/filterTypes";
import {storageKeys} from "../../../utils/constants";

const isDateString = (value: any) =>
  typeof value === "string" && !isNaN(Date.parse(value)) && value.includes("T");

// Saved filters should not persist the free-text search ("q") to avoid ghost filters.
const stripSearchFilters = (filters: any[] = []) =>
  filters.filter((f: any) => {
    const k = String(f?.key ?? "");
    return !(k === "q" || k === "search" || k.startsWith("filters[q]"));
  });

const deserializeDates = (filter: SavedFilter): SavedFilter => {
  const {searchQuery:_searchQuery, ...rest} = filter as any; // strip legacy field

  return {
    ...rest,
    filters: stripSearchFilters(rest.filters ?? []).map((f: any) => ({
      ...f,
      value:
        f.value?.startDate && f.value?.endDate
          ? {startDate: new Date(f.value.startDate), endDate: new Date(f.value.endDate)}
          : isDateString(f.value)
            ? new Date(f.value)
            : f.value
    })),
    createdAt: new Date(rest.createdAt)
  };
};

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

  const saveFilter = (name: string, description: string, filters: any[]) => {
    savedFilters.value = [
      ...savedFilters.value,
      {
        id: `saved_${Date.now()}`,
        name,
        description,
        filters: stripSearchFilters(filters),
        createdAt: new Date()
      }
    ];
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
    deleteSavedFilter
  };
}