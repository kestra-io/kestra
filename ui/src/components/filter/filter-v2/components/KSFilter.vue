<template>
    <section class="filter-section">
        <div 
            class="top-container" 
            :class="searchInputFullWidth ? 'top-container-full-width' : 'top-container-normal'"
        >
            <LeftFilter
                :configuration="configuration"
                :appliedFilters="appliedFilters"
                :searchQuery="searchQuery"
                :showSearchInput="showSearchInput"
                :searchInputFullWidth="searchInputFullWidth"
                @update:search-query="searchQuery = $event"
                @add-filter="addFilter"
                @remove-filter="removeFilter"
                @update-filter="updateFilter"
                @close-customize="isCustomizeFiltersVisible = false"
            />

            <RightFilter
                :hasFilterKeys="hasFilterKeys"
                :hasAppliedFilters="hasAppliedFilters"
                :appliedFilters="appliedFilters"
                :editingFilter="editingFilter"
                :buttons="buttons"
                :savedFilters="savedFilters"
                :showOptions="showOptions"
                :searchInputFullWidth="searchInputFullWidth"
                @save-filter="saveFilter"
                @update-saved-filter="updateSavedFilter"
                @close-edit-filter="closeEditFilter"
                @load-saved-filter="loadSavedFilter"
                @edit-saved-filter="editSavedFilter"
                @delete-saved-filter="deleteSavedFilter"
                @toggle-options="toggleOptions"
            />
        </div>

        <ExpandOptions
            :showOptions="showOptions"
            :buttons="buttons"
            :tableOptions="tableOptions"
            :chartVisible="chartVisible"
            :properties="properties"
            @update-chart="updateChart"
            @refresh-data="refreshData"
            @update-properties="updateProperties"
        />
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue";
    import {useRoute} from "vue-router";
    import {useFilters} from "../composable/useFilters";
    import {useSavedFilters} from "../composable/useSavedFilters";
    import {useDataOptions} from "../composable/useDataOptions";
    import {
        FilterConfiguration,
        AppliedFilter,
        SavedFilter,
        FilterProperties,
        TableOptions,
        FilterButtons
    } from "../utils/types";

    import LeftFilter from "./LeftFilter.vue";
    import RightFilter from "./RightFilter.vue";
    import ExpandOptions from "./ExpandOptions.vue";

    const props = withDefaults(defineProps<{
        configuration: FilterConfiguration;
        buttons?: FilterButtons;
        tableOptions?: TableOptions;
        properties?: FilterProperties;
        prefix?: string;
        showSearchInput?: boolean;
        searchInputFullWidth?: boolean;
    }>(), {
        buttons: () => ({}),
        tableOptions: () => ({}),
        properties: () => ({shown: false}),
        prefix: "",
        showSearchInput: true,
        searchInputFullWidth: false
    });

    const emits = defineEmits<{
        search: [query: string];
        filter: [filters: AppliedFilter[]];
        refresh: [];
        updateProperties: [columns: string[]];
    }>();

    const route = useRoute();

    const {
        appliedFilters, 
        searchQuery,
        addFilter, 
        removeFilter, 
        updateFilter
    } = useFilters(props.configuration, props.prefix, props.showSearchInput);

    const {
        savedFilters, 
        loadSavedFilters, 
        saveFilter, 
        updateSavedFilter, 
        deleteSavedFilter
    } = useSavedFilters(props.prefix);

    const {
        showOptions, 
        chartVisible, 
        toggleOptions, 
        updateChart, 
        refreshData: tableRefreshData
    } = useDataOptions(props.tableOptions);

    const isCustomizeFiltersVisible = ref(false);
    const isSavedFiltersVisible = ref(false);
    const editingFilter = ref<SavedFilter | undefined>(undefined);

    const hasFilterKeys = computed(() => props.configuration.keys?.length > 0);
    const hasAppliedFilters = computed(() => appliedFilters.value?.length > 0);

    const loadSavedFilter = (savedFilter: SavedFilter) => {
        appliedFilters.value.forEach(filter => removeFilter(filter.id));
        savedFilter.filters.forEach(addFilter);
        isSavedFiltersVisible.value = false;
    };

    const editSavedFilter = (savedFilter: SavedFilter) => {
        editingFilter.value = savedFilter;
    };

    const closeEditFilter = () => {
        editingFilter.value = undefined;
    };

    const refreshData = () => {
        tableRefreshData();
        emits("refresh");
    };

    const updateProperties = (columns: string[]) => {
        emits("updateProperties", columns);
    };

    onMounted(() => {
        loadSavedFilters();
    });

    watch(searchQuery, (newQuery) => {
        if (props.showSearchInput) {
            emits("search", newQuery);
        }
    });

    watch(appliedFilters, (newFilters) => {
        emits("filter", newFilters);
    }, {deep: true});

    watch(() => route.name, loadSavedFilters, {immediate: false});
    watch(() => props.prefix, loadSavedFilters, {immediate: false});
</script>

<style lang="scss" scoped>
section {
    &.filter-section {
        display: flex;
        flex-direction: column;
        margin-bottom: 1rem;
        width: 100%;
        border: 1px solid var(--bs-border-color);
        border-radius: 0.5rem;
        background-color: var(--ks-background-panel);
        box-shadow: 2px 3px 3px 0px var(--ks-card-shadow);

        .top-container {
            display: flex;
            align-items: center;
            row-gap: 1rem;
            flex-wrap: wrap;
            padding: 1rem;

            &.top-container-normal {
                justify-content: space-between;
            }

            &.top-container-full-width {
                justify-content: flex-start;
            }

            .filter-popover {
                box-shadow: 2px 3px 3px 0px #0000004A;
            }
        }
    }
}

.filter-search {
    max-width: 210px;

    ::placeholder {
        color: var(--ks-content-tertiary);
        font-size: 14px;
        line-height: 20px;
    }

    :deep(svg) {
        color: var(--ks-content-tertiary) !important;
        position: absolute;
        bottom: -0.10rem;
        font-size: 18px;
    }
}

:deep(.el-popover.el-popper), :deep(.el-popper) {
    padding: 0 !important;
}

:deep(.el-input__wrapper) {
    .el-input__icon {
        color: var(--ks-content-tertiary);
    }

    .el-input__inner::placeholder {
        color: var(--ks-content-tertiary);
        font-size: 14px;
    }
}
</style>