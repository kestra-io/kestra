<template>
    <section class="filter">
        <div class="top" :class="{options: showOptions}">
            <MainFilter />
            <RightFilter>
                <template #extra>
                    <slot name="extra" />
                </template>
            </RightFilter>
        </div>
  
        <FilterOptions v-if="showOptions && buttons?.tableOptions?.shown !== false" />
    </section>
</template>
  
  <script setup lang="ts">
    import {ref, computed, provide, onMounted, watch} from "vue";
    import {useFilters} from "../composables/useFilters";
    import {useSavedFilters} from "../composables/useSavedFilters";
    import {useDataOptions} from "../composables/useDataOptions";
  
    import type {
        SavedFilter,
        TableOptions,
        AppliedFilter,
        TableProperties,
        FilterConfiguration,
    } from "../utils/filterTypes";
  
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";
  
    import MainFilter from "./MainFilter.vue";
    import RightFilter from "./RightFilter.vue";
    import FilterOptions from "./FilterOptions.vue";
  
    const props = withDefaults(
        defineProps<{
            configuration: FilterConfiguration;
            buttons?: {
                savedFilters?: { shown?: boolean };
                tableOptions?: { shown?: boolean };
            };
            tableOptions?: TableOptions;
            properties?: TableProperties;
            prefix?: string;
            showSearchInput?: boolean;
            searchInputFullWidth?: boolean;
            legacyQuery?: boolean;
            readOnly?: boolean;
            defaultScope?: boolean;
            defaultTimeRange?: boolean;
        }>(),
        {
            buttons: () => ({}),
            tableOptions: () => ({}),
            properties: () => ({shown: false}),
            prefix: "",
            showSearchInput: true,
            searchInputFullWidth: false,
            legacyQuery: false,
            readOnly: false,
            defaultScope: undefined,
            defaultTimeRange: undefined,
        },
    );
  
    const emits = defineEmits<{
        refresh: [];
        search: [query: string];
        filter: [filters: AppliedFilter[]];
        updateProperties: [columns: string[]];
    }>();
  
    const {
        appliedFilters,
        searchQuery,
        addFilter,
        removeFilter,
        updateFilter,
        resetToPreApplied,
        hasPreApplied,
        getPreApplied,
    } = useFilters(
        props.configuration,
        props.showSearchInput,
        props.legacyQuery,
        props.defaultScope,
        props.defaultTimeRange,
    );
  
    const {savedFilters, saveFilter, updateSavedFilter, deleteSavedFilter} =
        useSavedFilters(props.prefix);
  
    const {
        showOptions,
        chartVisible,
        toggleOptions,
        updateChart,
        refreshData: tableRefreshData,
    } = useDataOptions(props.tableOptions);
  
    const editingFilter = ref<SavedFilter | undefined>(undefined);
  
    const hasFilterKeys = computed(() => (props.configuration.keys?.length ?? 0) > 0);
    const hasAppliedFilters = computed(() => (appliedFilters.value?.length ?? 0) > 0);
  
    /**
     * Defensive: saved filters should never re-apply "search" state.
     * Source of truth is useSavedFilters.ts (it strips search on save/deserialize),
     * but we also guard here to protect against legacy/dirty saved filters.
     *
     * Intentionally does NOT touch router/URL. This keeps concerns separated.
     */
    const isSearchLikeKey = (raw: unknown) => {
        const key = String(raw ?? "");
        return key === "q" || key === "search" || key.startsWith("filters[q]");
    };

    const loadSavedFilter = (savedFilter: SavedFilter) => {
        // Clear current filters
        for (const f of appliedFilters.value) {
            removeFilter(f.id);
        }

        // Apply saved filters (excluding search-like keys)
        for (const f of savedFilter.filters ?? []) {
            if (!isSearchLikeKey(f.key)) {
                addFilter(f);
            }
        }

        // NOTE: Never mutate searchQuery here.
        // If the user typed something, keep it. If empty, leave it empty.
    };
  
    const refreshData = () => {
        tableRefreshData();
        emits("refresh");
    };
  
    provide(FILTER_CONTEXT_INJECTION_KEY, {
        configuration: computed(() => props.configuration),
        appliedFilters,
        searchQuery,
        savedFilters,
        editingFilter,
        hasFilterKeys,
        hasAppliedFilters,
        buttons: computed(() => props.buttons),
        readOnly: computed(() => props.readOnly),
        properties: computed(() => props.properties),
        tableOptions: computed(() => props.tableOptions),
        showSearchInput: computed(() => props.showSearchInput),
        searchInputFullWidth: computed(() => props.searchInputFullWidth),
        showOptions,
        chartVisible,
        addFilter,
        removeFilter,
        updateFilter,
        saveFilter,
        updateSavedFilter,
        deleteSavedFilter,
        loadSavedFilter,
        toggleOptions,
        updateChart,
        refreshData,
        resetToPreApplied,
        hasPreApplied,
        getPreApplied,
        editSavedFilter: (filter: SavedFilter) => {
            editingFilter.value = filter;
        },
        closeEditFilter: () => {
            editingFilter.value = undefined;
        },
        updateProperties: (columns: string[]) => {
            emits("updateProperties", columns);
        },
    });
  
    onMounted(() => {
        if (props.showSearchInput && searchQuery.value) {
            emits("search", searchQuery.value);
        }
        if (appliedFilters.value.length > 0) {
            emits("filter", appliedFilters.value);
        }
    });
  
    watch(searchQuery, (newQuery) => {
        if (props.showSearchInput) {
            emits("search", newQuery);
        }
    });
  
    watch(
        appliedFilters,
        (newFilters) => {
            emits("filter", newFilters);
        },
        {deep: true},
    );
  </script>
  
  <style lang="scss" scoped>
  .filter {
    display: flex;
    flex-direction: column;
    margin-bottom: 1rem;
    width: 100%;
    border-radius: 0.5rem;
  
    .top {
      display: flex;
      align-items: flex-start;
      flex-wrap: nowrap;
      gap: 0.5rem;
  
      &.options {
        padding-bottom: 1rem;
      }
  
      @media (max-width: 768px) {
        flex-wrap: wrap;
      }
    }
  }
  </style>
  