<template>
    <div class="filter-right-container" :class="searchInputFullWidth ? 'filter-right-grow' : ''">
        <el-popover
            v-if="configuration.keys && configuration.keys.length > 0"
            v-model:visible="isCustomizeFiltersVisible"
            placement="bottom-start"
            :width="328"
            trigger="click"
            class="filter-popover"
            :showArrow="false"
            @hide="closeCustomize"
        >
            <template #reference>
                <el-button :icon="FilterOutline" size="default" class="customize-button">
                    Customize filters
                </el-button>
            </template>

            <CustomizeFilters
                :configuration="configuration"
                :appliedFilters="appliedFilters"
                @add-filter="handleAddFilter"
                @remove-filter="handleRemoveFilter"
                @close="closeCustomize"
            />
        </el-popover>

        <el-tooltip content="Reset all filters" placement="top">
            <el-button 
                :icon="Refresh" 
                circle 
                class="refresh-button" 
                @click="handleReset" 
                :disabled="!internalSearchQuery && appliedFilters.length === 0" 
            />
        </el-tooltip>

        <div class="search-container" :class="searchInputFullWidth ? 'search-grow' : ''" v-if="showSearchInput">
            <el-input
                v-model="internalSearchQuery"
                :placeholder="configuration.searchPlaceholder"
                clearable
                :prefixIcon="Magnify"
                @input="handleSearchUpdate"
                :class="searchInputFullWidth ? 'full-width' : ''"
            />
        </div>

        <div class="filters-container" :class="searchInputFullWidth ? 'filters-shrink' : ''">
            <FilterChip
                v-for="filter in appliedFilters"
                :key="filter.id"
                :filter="filter"
                :filterKey="getFilterKeyConfig(filter)"
                @remove="handleRemoveFilter"
                @update="handleUpdateFilter"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {FilterConfiguration, AppliedFilter} from "../utils/types";
    import FilterChip from "./FilterChip.vue";
    import CustomizeFilters from "./CustomizeFilters.vue";
    import FilterOutline from "vue-material-design-icons/FilterOutline.vue";
    import Refresh from "vue-material-design-icons/Refresh.vue";
    import {Magnify} from "../../utils/icons";

    const props = defineProps<{
        configuration: FilterConfiguration;
        appliedFilters: AppliedFilter[];
        searchQuery: string;
        showSearchInput: boolean;
        searchInputFullWidth: boolean;
    }>();

    const emits = defineEmits<{
        "update:search-query": [value: string];
        "add-filter": [filter: AppliedFilter];
        "remove-filter": [id: string];
        "update-filter": [filter: AppliedFilter];
        "close-customize": [];
        "reset-filters": [];
    }>();

    const isCustomizeFiltersVisible = ref(false);
    const internalSearchQuery = ref(props.searchQuery);

    const getFilterKeyConfig = (filter: AppliedFilter) =>
        props.configuration.keys?.find(key => key.key === filter.key) || null;

    const handleSearchUpdate = (value: string) => {
        emits("update:search-query", value);
    };

    const handleAddFilter = (filter: AppliedFilter) => {
        emits("add-filter", filter);
    };

    const handleRemoveFilter = (id: string) => {
        emits("remove-filter", id);
    };

    const handleUpdateFilter = (filter: AppliedFilter) => {
        emits("update-filter", filter);
    };

    const closeCustomize = () => {
        isCustomizeFiltersVisible.value = false;
        emits("close-customize");
    };

    const handleReset = () => {
        internalSearchQuery.value = "";
        emits("update:search-query", "");
        for (const filter of props.appliedFilters) {
            emits("remove-filter", filter.id);
        }
        emits("reset-filters");
    };
</script>

<style lang="scss" scoped>
.filter-right-container {
    display: flex;
    align-items: center;
    justify-content: flex-start;

    &.filter-right-grow {
        flex-grow: 1;
    }

    .filter-popover {
        box-shadow: 2px 3px 3px 0px #0000004A;
    }

    .customize-button {
        background-color: var(--ks-button-background-secondary);
        font-size: 14px;

        :deep(svg) {
            color: var(--ks-content-tertiary) !important;
            font-size: 16px;
            position: absolute;
            bottom: -0.09rem;
        }
    }

    .refresh-button {
        margin-right: 12px !important;
    }

    .search-container {
        position: relative;
        margin-right: 0.5rem;

        &.search-grow {
            flex-grow: 1;
        }

    }

    .filters-container {
        display: flex;
        align-items: center;
        gap: 1rem;

        &.filters-shrink {
            flex-shrink: 0;
        }
    }
}
</style>