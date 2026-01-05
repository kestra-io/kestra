<template>
    <div
        class="filter-container"
        :class="{'filter-shrink': filter.searchInputFullWidth.value}"
    >
        <el-button
            v-if="filter.tableOptions.value?.refresh?.shown"
            @click="filter.refreshData"
            :icon="Refresh"
            :size="'default'"
            class="refresh-button"
        >
            {{ $t("filter.refresh") }}
        </el-button>

        <SaveFilters
            v-if="!filter.searchInputFullWidth.value"
            :disabled="
                (!filter.hasAppliedFilters.value && !filter.searchQuery.value) || filter.readOnly.value
            "
            :appliedFilters="filter.appliedFilters.value"
            :editingFilter="filter.editingFilter.value"
            :savedFilters="filter.savedFilters.value"
            @save="handleSave"
            @edit="handleEdit"
            @close-edit="filter.closeEditFilter"
        />

        <el-popover
            v-if="filter.buttons.value?.savedFilters?.shown !== false"
            v-model:visible="isSavedFiltersVisible"
            placement="bottom-end"
            trigger="click"
            :popperClass="'p-0'"
            :width="300"
            :showArrow="false"
            :disabled="filter.readOnly.value"
            @hide="isSavedFiltersVisible = false"
        >
            <template #reference>
                <el-button type="default" size="default" class="saved-btn" :icon="BookmarkCheckOutline" :disabled="filter.readOnly.value">
                    <el-tooltip :content="$t('filter.saved tooltip')" placement="top" effect="light">
                        <span class="saved-content">
                            {{ $t("filter.saved") }}
                            <el-tag type="primary" effect="light" class="saved-count">
                                {{ filter.savedFilters.value.length }}
                            </el-tag>
                            <el-icon class="el-icon--right">
                                <ChevronDown />
                            </el-icon>
                        </span>
                    </el-tooltip>
                </el-button>
            </template>

            <SavedFilters
                :savedFilters="filter.savedFilters.value"
                @load="handleLoad"
                @edit="filter.editSavedFilter"
                @delete="filter.deleteSavedFilter"
                @close="isSavedFiltersVisible = false"
            />
        </el-popover>

        <el-tooltip :content="$t('filter.show data options tooltip')" placement="top" effect="light">
            <el-button
                v-if="filter.buttons.value?.tableOptions?.shown !== false"
                type="default"
                size="default"
                @click="filter.toggleOptions"
                class="options-btn"
                :icon="VerticalSliders"
            />
        </el-tooltip>

        <slot name="extra" />
    </div>
</template>

<script setup lang="ts">
    import {ref, inject} from "vue";
    import {ChevronDown, BookmarkCheckOutline, Refresh} from "../utils/icons";
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";
    
    import SaveFilters from "../segments/SaveFilters.vue";
    import SavedFilters from "../segments/SavedFilters.vue";
    import VerticalSliders from "../../../assets/icons/VerticalSliders.vue";

    const isSavedFiltersVisible = ref(false);
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!;

    const handleSave = (name: string, description: string) => {
        filter.saveFilter(
            name,
            description,
            filter.appliedFilters.value,
            filter.searchQuery.value
        );
    };

    const handleEdit = (id: string, name: string, description: string) => {
        filter.updateSavedFilter(id, name, description);
    };

    const handleLoad = (savedFilter: any) => {
        filter.loadSavedFilter(savedFilter);
        isSavedFiltersVisible.value = false;
    };
</script>

<style lang="scss" scoped>
.filter-container {
    --ks-box-shadow: 0 1px 2px var(--ks-card-shadow);

    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: .5rem;
    flex-shrink: 0;
    min-width: fit-content;

    &.filter-shrink {
        flex-shrink: 0;
    }

    .saved-btn {
        box-shadow: none;
        margin: 0;
        font-size: 0.875rem;
        box-shadow: var(--ks-box-shadow);

        .saved-content {
            display: flex;
            align-items: center;
            gap: 0.25rem;
        }

        .saved-count {
            margin-left: 0.375rem;
            background-color: var(--ks-tag-background);
            &:hover {
                background-color: var(--ks-tag-background-hover);
            }
            color: var(--ks-content-secondary);
            border-radius: 0.35rem;
            font-size: 0.625rem;
            padding: 0.5rem 0.5625rem;
        }
    }

    .options-btn {
        box-shadow: var(--ks-box-shadow);
        margin: 0;
        padding: 0.5rem;
        border-radius: 0.25rem;
        font-size: 1rem;
        color: var(--ks-content-primary) !important;
    }

    .refresh-button {
        background-color: transparent;
        border: none;
        box-shadow: none;
        margin: 0;
        padding: 0.25rem 0.5rem;
        font-size: 12px;

        :deep(svg) {
            color: var(--ks-content-tertiary);
        }

        &:hover {
            background-color: var(--ks-tag-background);
        }
    }
}
</style>