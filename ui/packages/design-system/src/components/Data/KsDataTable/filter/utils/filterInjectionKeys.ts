import type {ComputedRef, InjectionKey, Ref} from "vue"
import type {FilterConfiguration, AppliedFilter, FilterGroup, LogicalOperator, SavedFilter, TableOptions, TableProperties} from "./filterTypes"

export interface FilterContext {
    searchQuery: Ref<string>;
    editingFilter: Ref<SavedFilter | undefined>;

    readOnly: ComputedRef<boolean>;
    chartVisible: ComputedRef<boolean>;
    hasFilterKeys: ComputedRef<boolean>;
    showSearchInput: ComputedRef<boolean>;
    hasAppliedFilters: ComputedRef<boolean>;
    hasDismissedDefaultVisibleKeys: ComputedRef<boolean>;
    tableOptions: ComputedRef<TableOptions>;
    savedFilters: ComputedRef<SavedFilter[]>;
    properties: ComputedRef<TableProperties>;
    searchInputFullWidth: ComputedRef<boolean>;
    appliedFilters: ComputedRef<AppliedFilter[]>;
    groups: ComputedRef<FilterGroup[]>;
    topLogical: ComputedRef<LogicalOperator>;
    hasUnrenderableFilters: ComputedRef<boolean>;
    rawQuery: ComputedRef<string>;
    viewMode: Ref<"chip" | "raw">;
    configuration: ComputedRef<FilterConfiguration>;
    buttons: ComputedRef<{
        savedFilters?: {shown?: boolean};
        tableOptions?: {shown?: boolean};
    }>;

    refreshData: () => void;
    closeEditFilter: () => void;
    removeFilter: (id: string) => void;
    updateChart: (value: boolean) => void;
    addFilter: (filter: AppliedFilter, groupId?: string) => void;
    updateFilter: (filter: AppliedFilter) => void;
    moveFilter: (filterId: string, targetGroupId: string) => void;
    placeFilter: (filterId: string, targetLeafId: string, targetIndex: number) => void;
    wrapGroups: (sourceGroupId: string, targetGroupId: string) => void;
    unwrapGroup: (wrapperId: string) => void;
    setTopLogical: (op: LogicalOperator) => void;
    setWrapperLogical: (wrapperId: string, op: LogicalOperator) => void;
    applyRawQuery: (str: string) => void;
    setViewMode: (mode: "chip" | "raw") => void;
    addGroup: () => void;
    removeGroup: (groupId: string) => void;
    loadSavedFilter: (filter: SavedFilter) => void;
    editSavedFilter: (filter: SavedFilter) => void;
    updateProperties: (columns: string[]) => void;
    deleteSavedFilter: (filter: SavedFilter) => void;
    resetToDefaults: () => void;
    clearFilters: () => void;
    hasPreApplied: (filterKey: string) => boolean;
    getPreApplied: (filterKey: string) => AppliedFilter | undefined;
    updateSavedFilter: (id: string, name: string, description: string) => void;
    saveFilter: (name: string, description: string, filters: AppliedFilter[]) => void;
}

export const FILTER_CONTEXT_INJECTION_KEY = Symbol("filter-context-injection-key") as InjectionKey<FilterContext>
