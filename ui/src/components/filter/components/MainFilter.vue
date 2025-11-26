<template>
    <div class="filter-container" :class="{'filter-grow': filter.searchInputFullWidth?.value}">
        <el-popover
            v-if="filter.hasFilterKeys?.value"
            v-model:visible="isCustomizeFiltersVisible"
            placement="bottom-start"
            trigger="click"
            :width="300"
            :popperClass="'p-0'"
            :showArrow="false"
            :disabled="filter.readOnly?.value"
            @hide="isCustomizeFiltersVisible = false"
        >
            <template #reference>
                <el-button
                    :icon="FilterOutline"
                    size="default"
                    class="customize-button"
                    :disabled="filter.readOnly?.value"
                >
                    <el-tooltip
                        placement="top"
                        effect="light"
                        :content="$t('filter.customize tooltip')"
                        :disabled="filter.readOnly?.value"
                    >
                        <span>{{ $t("filter.customize") }}</span>
                    </el-tooltip>
                </el-button>
            </template>

            <CustomizeFilters
                :configuration="filter.configuration?.value"
                :appliedFilters="filter.appliedFilters?.value"
                @add-filter="handleAddFilter"
                @remove-filter="filter.removeFilter"
                @close="isCustomizeFiltersVisible = false"
            />
        </el-popover>

        <div
            v-if="filter.showSearchInput?.value"
            class="search-container"
            :class="{
                'search-grow': filter.searchInputFullWidth?.value,
                'read-only': filter.readOnly?.value
            }"
        >
            <SearchInput
                :modelValue="filter.searchQuery?.value"
                @update:model-value="debouncedUpdateSearch"
                :placeholder="filter.configuration?.value?.searchPlaceholder"
                :fullWidth="filter.searchInputFullWidth?.value"
            />
        </div>

        <FilterChip
            v-for="appliedFilter in filter.appliedFilters?.value"
            :key="appliedFilter.id"
            :ref="el => setChipRef(appliedFilter.id, el)"
            :filter="appliedFilter"
            :filterKey="getFilterKeyConfig(appliedFilter)"
            :class="{
                'filters-hidden': filter.searchInputFullWidth?.value,
                'read-only': filter.readOnly?.value
            }"
            class="filter-chip"
            @remove="filter.removeFilter"
            @update="filter.updateFilter"
        />

        <el-tooltip
            v-if="filter.hasFilterKeys?.value"
            placement="top"
            effect="light"
            :content="$t('filter.reset_all')"
            :disabled="filter.readOnly?.value"
        >
            <el-button
                link
                class="refresh-btn"
                @click="handleReset"
                :disabled="!canReset || filter.readOnly?.value"
            >
                {{ $t("filter.reset") }}
            </el-button>
        </el-tooltip>
    </div>
</template>

<script setup lang="ts">
    import {ref, inject, nextTick, computed} from "vue";
    import {useDebounceFn} from "@vueuse/core";
    
    import {FilterOutline} from "../utils/icons";

    import FilterChip from "./layout/FilterChip.vue";
    import SearchInput from "./layout/SearchInput.vue";
    import CustomizeFilters from "../segments/CustomizeFilters.vue";

    import {AppliedFilter} from "../utils/filterTypes";
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";

    const isCustomizeFiltersVisible = ref(false);
    const chipRefs = ref<Record<string, any>>({});
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!;

    const canReset = computed(() => {
        return filter.hasAppliedFilters?.value ?? !!filter.searchQuery?.value;
    });

    const getFilterKeyConfig = (appliedFilter: any) => {
        return filter.configuration.value.keys?.find((key: any) => key.key === appliedFilter.key) ?? null;
    };

    const setChipRef = (filterId: string, el: any) => el 
        ? chipRefs.value[filterId] = el 
        : delete chipRefs.value[filterId];

    const handleAddFilter = (newFilter: AppliedFilter) => {
        filter.addFilter(newFilter);
        setTimeout(() => {
            isCustomizeFiltersVisible.value = false;
        }, 300);
        nextTick(() => chipRefs.value[newFilter.id]?.editPopover?.toggleDialog());
    };

    const handleReset = () => {
        filter.resetToPreApplied();
    };

    const debouncedUpdateSearch = useDebounceFn((value: string) => {
        filter.searchQuery.value = value;
    }, 700);
</script>

<style lang="scss" scoped>
.filter-container {
    --ks-box-shadow: 0 1px 2px var(--ks-card-shadow);

    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: .5rem;
    row-gap: 0.5rem;
    flex: 1;
    min-width: 0;

    &.filter-grow {
        flex-wrap: nowrap;
        flex-grow: 1;
    }
}

.customize-button {
    background-color: var(--ks-button-background-secondary);
    font-size: 0.75rem;
    flex-shrink: 0;
    box-shadow: var(--ks-box-shadow);

    &:hover {
        background-color: var(--ks-button-background-secondary-hover);
    }

    :deep(svg) {
        color: var(--ks-content-tertiary) !important;
        font-size: 1.125rem;
        position: absolute;
        bottom: -0.24rem;
        right: 0.125rem;
    }
}

.refresh-btn {
    margin: 0 !important;
    font-size: 12px;
    color: var(--ks-content-secondary);

    &:hover {
        color: var(--ks-content-primary);
        text-decoration: underline;
    }

}

.search-container {
    position: relative;
    flex: 0 0 200px;
    min-width: 150px;
    max-width: 200px;

    &.search-grow {
        flex: 2 1 auto;
        max-width: none;
        min-width: 200px;
    }

    &.read-only {
        pointer-events: none;
        opacity: 0.6;
    }
}

.filter-chip {
    flex-shrink: 0;
    box-shadow: 0 1px 2px var(--ks-card-shadow);

    &.filters-hidden {
        display: none;
    }

    &.read-only {
        pointer-events: none;
        opacity: 0.6;
    }
}
</style>