import type {ComputedRef, InjectionKey, Ref} from "vue";
import {FilterConfiguration, AppliedFilter, SavedFilter, TableOptions, TableProperties} from "./filterTypes";

export interface FilterContext {
    searchQuery: Ref<string>;
    editingFilter: Ref<SavedFilter | undefined>;

    readOnly: ComputedRef<boolean>;
    showOptions: ComputedRef<boolean>;
    chartVisible: ComputedRef<boolean>;
    hasFilterKeys: ComputedRef<boolean>;
    showSearchInput: ComputedRef<boolean>;
    hasAppliedFilters: ComputedRef<boolean>;
    tableOptions: ComputedRef<TableOptions>;
    savedFilters: ComputedRef<SavedFilter[]>;
    properties: ComputedRef<TableProperties>;
    searchInputFullWidth: ComputedRef<boolean>;
    appliedFilters: ComputedRef<AppliedFilter[]>;
    configuration: ComputedRef<FilterConfiguration>;
    buttons: ComputedRef<{
        savedFilters?: {shown?: boolean};
        tableOptions?: {shown?: boolean};
    }>;

    refreshData: () => void;
    toggleOptions: () => void;
    closeEditFilter: () => void;
    removeFilter: (id: string) => void;
    updateChart: (value: boolean) => void;
    addFilter: (filter: AppliedFilter) => void;
    updateFilter: (filter: AppliedFilter) => void;
    loadSavedFilter: (filter: SavedFilter) => void;
    editSavedFilter: (filter: SavedFilter) => void;
    updateProperties: (columns: string[]) => void;
    deleteSavedFilter: (filter: SavedFilter) => void;
    resetToPreApplied: () => void;
    hasPreApplied: (filterKey: string) => boolean;
    getPreApplied: (filterKey: string) => AppliedFilter | undefined;
    updateSavedFilter: (id: string, name: string, description: string) => void;
    saveFilter: (name: string, description: string, filters: AppliedFilter[], searchQuery?: string) => void;
}

export const FILTER_CONTEXT_INJECTION_KEY = Symbol("filter-context-injection-key") as InjectionKey<FilterContext>;