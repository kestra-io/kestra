<template>
    <div class="filter-left-container" :class="searchInputFullWidth ? 'filter-left-shrink' : ''">
        <el-button-group
            v-if="hasFilterKeys"
            class="button-group"
        >
            <SaveFilterButton
                v-if="hasFilterKeys"
                :disabled="!hasAppliedFilters"
                :appliedFilters="appliedFilters"
                :editingFilter="editingFilter"
                @save="handleSave"
                @edit="handleEdit"
                @close-edit="handleCloseEdit"
            />
        </el-button-group>

        <el-popover
            v-if="buttons?.savedFilters?.shown !== false"
            v-model:visible="isSavedFiltersVisible"
            placement="bottom-end"
            trigger="click"
            :width="328"
            @hide="closeSavedFilters"
        >
            <template #reference>
                <el-button size="default" :icon="ChevronDown" class="saved-filters-button">
                    Saved filters
                    <el-tag type="primary" effect="light" class="saved-count">
                        {{ savedFilters.length }}
                    </el-tag>
                </el-button>
            </template>

            <SavedFiltersPanel
                :savedFilters="savedFilters"
                @load="handleLoad"
                @edit="handleEditSaved"
                @delete="handleDelete"
                @close="closeSavedFilters"
            />
        </el-popover>

        <el-button
            v-if="buttons?.tableOptions?.shown !== false"
            @click="handleToggleOptions"
            class="data-options-button"
            :icon="TablePlus"
        >
            Table Options<el-icon class="toggle-icon">
                <component :is="showOptions ? ChevronUp : ChevronDown" />
            </el-icon>
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import {AppliedFilter, SavedFilter, FilterButtons} from "../utils/types";
    import {ChevronDown, ChevronUp} from "../../utils/icons";
    import TablePlus from "vue-material-design-icons/TablePlus.vue";
    import SaveFilterButton from "./SaveFilters.vue";
    import SavedFiltersPanel from "./SavedFilters.vue";

    const props = defineProps<{
        hasFilterKeys: boolean;
        hasAppliedFilters: boolean;
        appliedFilters: AppliedFilter[];
        editingFilter: SavedFilter | undefined;
        buttons?: FilterButtons;
        savedFilters: SavedFilter[];
        showOptions: boolean;
        searchInputFullWidth: boolean;
    }>();

    const emits = defineEmits<{
        "save-filter": [name: string, description: string, filters: AppliedFilter[]];
        "update-saved-filter": [id: string, name: string, description: string];
        "close-edit-filter": [];
        "load-saved-filter": [filter: SavedFilter];
        "edit-saved-filter": [filter: SavedFilter];
        "delete-saved-filter": [filter: SavedFilter];
        "toggle-options": [];
    }>();

    const isSavedFiltersVisible = ref(false);

    const handleSave = (name: string, description: string) => {
        emits("save-filter", name, description, props.appliedFilters);
    };

    const handleEdit = (id: string, name: string, description: string) => {
        emits("update-saved-filter", id, name, description);
    };

    const handleCloseEdit = () => {
        emits("close-edit-filter");
    };

    const handleLoad = (filter: SavedFilter) => {
        emits("load-saved-filter", filter);
        closeSavedFilters();
    };

    const handleEditSaved = (filter: SavedFilter) => {
        emits("edit-saved-filter", filter);
    };

    const handleDelete = (filter: SavedFilter) => {
        emits("delete-saved-filter", filter);
    };

    const handleToggleOptions = () => {
        emits("toggle-options");
    };

    const closeSavedFilters = () => {
        isSavedFiltersVisible.value = false;
    };
</script>

<style lang="scss" scoped>
.filter-left-container {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 1rem;

    &.filter-left-shrink {
        flex-shrink: 0;
    }

    .button-group {
        display: inline-flex;
        margin-right: 0.25rem;
    }

    .saved-filters-button {
        background-color: transparent;
        border: none;
        box-shadow: none;
        margin: 0;
        padding: 0;
        font-size: 14px;

        .saved-count {
            margin-left: 0.25rem;
            background-color: var(--ks-button-background-secondary);
            color: var(--ks-content-secondary);
            border-radius: 100%;
            font-size: 10px;
            padding: 10px;
        }
    }

    .data-options-button {
        border-radius: 32px;
        padding: 1rem;
        background-color: var(--ks-button-background-secondary);
        font-size: 14px;
        margin: 0;

        .toggle-icon {
            margin-left: 0.5rem;

            :deep(svg) {
                color: var(--ks-content-tertiary) !important;
                font-size: 1rem;
                position: absolute;
                bottom: -0.11rem;
            }
        }
    }
}
</style>