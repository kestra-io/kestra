<template>
    <section class="filter">
        <div class="top">
            <LeftFilter />
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
    import {useRoute} from "vue-router";
    import {useFilters} from "../composables/useFilters";
    import {useSavedFilters} from "../composables/useSavedFilters";
    import {useTableOptions} from "../composables/useTableOptions";
    
    import {
        SavedFilter,
        TableOptions,
        AppliedFilter,
        TableProperties,
        FilterConfiguration,
    } from "../utils/filterTypes";

    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";

    import LeftFilter from "./LeftFilter.vue";
    import RightFilter from "./RightFilter.vue";
    import FilterOptions from "./FilterOptions.vue";

    const props = withDefaults(defineProps<{
        configuration: FilterConfiguration;
        buttons?: {
            savedFilters?: {shown?: boolean}; 
            tableOptions?: {shown?: boolean}
        };
        tableOptions?: TableOptions;
        properties?: TableProperties;
        prefix?: string;
        showSearchInput?: boolean;
        searchInputFullWidth?: boolean;
        legacyQuery?: boolean;
        readOnly?: boolean;
    }>(), {
        buttons: () => ({}),
        tableOptions: () => ({}),
        properties: () => ({shown: false}),
        prefix: "",
        showSearchInput: true,
        searchInputFullWidth: false,
        legacyQuery: false,
        readOnly: false
    });

    const emits = defineEmits<{
        refresh: [];
        search: [query: string];
        filter: [filters: AppliedFilter[]];
        updateProperties: [columns: string[]];
    }>();

    const route = useRoute();

    const {appliedFilters, searchQuery, addFilter, removeFilter, updateFilter, resetToPreApplied} = useFilters(
        props.configuration,
        props.showSearchInput,
        props.legacyQuery
    );

    const {savedFilters, loadSavedFilters, saveFilter, updateSavedFilter, deleteSavedFilter} = useSavedFilters(
        props.prefix
    );

    const {showOptions, chartVisible, toggleOptions, updateChart, refreshData: tableRefreshData} = useTableOptions(
        props.tableOptions
    );

    const editingFilter = ref<SavedFilter | undefined>(undefined);

    const hasFilterKeys = computed(() => props.configuration.keys?.length > 0);
    const hasAppliedFilters = computed(() => appliedFilters.value?.length > 0);

    const loadSavedFilter = (savedFilter: SavedFilter) => {
        appliedFilters.value.forEach((filter) => {
            removeFilter(filter.id);
        });

        savedFilter.filters.forEach((filter) => {
            addFilter(filter);
        });

        searchQuery.value = savedFilter.searchQuery ?? "";
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
        editSavedFilter: (filter: SavedFilter) => {
            editingFilter.value = filter;
        },
        closeEditFilter: () => {
            editingFilter.value = undefined;
        },
        updateProperties: (columns: string[]) => {
            emits("updateProperties", columns);
        }
    });

    onMounted(() => {
        loadSavedFilters();
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

    watch(appliedFilters, (newFilters) => {
        emits("filter", newFilters);
    }, {deep: true});

    watch(() => route.name, loadSavedFilters);
    watch(() => props.prefix, loadSavedFilters);
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
        padding: 1rem 0;
    }
}
</style>