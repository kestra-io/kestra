<template>
    <el-button
        type="default"
        :disabled="disabled"
        @click="showSaveDialog = true"
        :icon="Save"
        class="no-bg-border"
    > 
        Save Filter 
    </el-button>

    <el-dialog
        v-model="showSaveDialog"
        :title="isEditMode ? 'Edit Filter' : 'Save Filter'"
        width="30%"
        class="custom-dialog"
        @close="closeSaveDialog"
    >
        <div class="save-filter-form">
            <div>
                <label>Label</label>
                <el-input
                    v-model="filterName"
                    placeholder="Enter filter label"
                />
            </div>

            <div>
                <label>Description</label>
                <el-input
                    v-model="filterDescription"
                    type="textarea"
                    placeholder="Enter filter description (optional)"
                    :rows="2"
                    maxlength="200"
                />
            </div>

            <div v-if="!isEditMode">
                <div class="filters-summary">
                    <div v-if="appliedFilters.length > 0" class="filters-list">
                        <div
                            v-for="filter in appliedFilters"
                            :key="filter.id"
                            class="filter-item"
                        >
                            <span class="filter-key">{{ filter.keyLabel }}</span>
                            <span class="filter-comparator">{{ filter.comparatorLabel }}</span>
                            <span class="filter-value">{{ filter.valueLabel }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div>
                <el-button @click="closeSaveDialog">
                    Cancel
                </el-button>
                <el-button
                    type="primary"
                    @click="saveFilter"
                    :disabled="!filterName.trim()"
                    :icon="Save"
                >
                    {{ isEditMode ? 'Update' : 'Save' }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {AppliedFilter, SavedFilter} from "../utils/types";
    import {Save} from "../../utils/icons";

    interface Props {
        disabled: boolean;
        appliedFilters: AppliedFilter[];
        editingFilter?: SavedFilter;
    }

    const props = defineProps<Props>();
    
    const emits = defineEmits<{
        save: [name: string, description: string, isGlobal?: boolean];
        edit: [id: string, name: string, description: string];
        "close-edit": [];
    }>();

    // State
    const showSaveDialog = ref(false);
    const filterName = ref("");
    const filterDescription = ref("");
    const makeGlobal = ref(false);

    const isEditMode = computed(() => !!props.editingFilter);

    watch(() => props.editingFilter, (newFilter, oldFilter) => {
        if (newFilter && !oldFilter) {
            filterName.value = newFilter.name;
            filterDescription.value = newFilter.description || "";
            makeGlobal.value = newFilter.global || false;
            showSaveDialog.value = true;
        } else if (!newFilter && oldFilter) {
            closeSaveDialog();
        }
    }, {immediate: true});

    const saveFilter = () => {
        if (!filterName.value.trim()) return;

        if (isEditMode.value && props.editingFilter) {
            emits("edit", props.editingFilter.id, filterName.value.trim(), filterDescription.value.trim());
        } else {
            emits("save", filterName.value.trim(), filterDescription.value.trim(), makeGlobal.value);
        }
        closeSaveDialog();
    };

    const closeSaveDialog = () => {
        showSaveDialog.value = false;
        filterName.value = "";
        filterDescription.value = "";
        makeGlobal.value = false;
        if (isEditMode.value) {
            emits("close-edit");
        }
    };
</script>

<style lang="scss" scoped>
    .save-filter-form {
        > div {
            margin-bottom: 1rem;

            &:last-child {
                margin-bottom: 0;
            }

            label {
                display: block;
                margin-bottom: 0.25rem;
                font-weight: 600;
                font-size: 14px;
                color: var(--ks-content-secondary);
            }
        }

        .filters-summary {
            padding: 0.5rem 0.75rem;
            background-color: var(--ks-surface-secondary);
            border-radius: 4px;
            border: 1px solid var(--ks-border-primary);
            min-height: 2rem;
        }

        .filters-list {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .filter-item {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 12px;

            .filter-key {
                color: var(--ks-content-primary);
                font-weight: 400;
            }

            .filter-comparator {
                color: var(--ks-chart-success);
                font-weight: 400;
            }

            .filter-value {
                color: var(--ks-content-primary);
                font-weight: 700;
            }
        }
    }

    .no-bg-border {
        background-color: transparent !important;
        border: none !important;
        box-shadow: none !important;
        margin: 0;
        padding: 0;
        font-size: 14px;
        color: var(--ks-content-link) !important;

        &:hover {
            color: var(--ks-content-link-hover) !important;
        }
    }

    .el-button.is-disabled {
        color: var(--ks-content-tertiary) !important;
        cursor: not-allowed !important;
    }

    .el-button-group .el-button--primary:last-child {
        border: none;
    }

    :deep(.el-input__inner::placeholder), :deep(.el-textarea__inner::placeholder) {
        color: var(--ks-content-tertiary);
        font-size: 14px;
    }

    :deep(footer.el-dialog__footer) {
        padding-top: 0 !important;
    }
</style>