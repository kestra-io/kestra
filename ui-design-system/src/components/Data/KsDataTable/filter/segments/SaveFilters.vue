<template>
    <ks-tooltip :content="$t('filter.save filter tooltip')" placement="top">
        <ks-button
            type="default"
            :disabled="disabled"
            @click="showSaveDialog = true"
            :icon="ContentSaveOutline"
            class="no-bg-border"
        />
    </ks-tooltip>

    <ks-dialog
        v-model="showSaveDialog"
        :title="isEditMode ? $t('filter.edit filter') : $t('filter.save filter')"
        class="custom-dialog"
        width="25%"
        @close="closeSaveDialog"
    >
        <div class="save-form">
            <ks-alert v-if="hasDuplicate" type="error" showIcon :closable="false">
                {{ $t("filter.save duplicate") }}
                <template #icon>
                    <CloseCircleOutline />
                </template>
            </ks-alert>
            <div>
                <label>{{ $t("filter.name") }}</label>
                <ks-input
                    v-model="filterName"
                    :placeholder="$t('filter.enter name')"
                    clearable
                />
            </div>

            <div>
                <label>{{ $t("filter.description") }}</label>
                <ks-input
                    v-model="filterDescription"
                    type="textarea"
                    :placeholder="$t('filter.enter description')"
                    :rows="2"
                    maxlength="200"
                />
            </div>

            <div v-if="!isEditMode">
                <div class="filter-summary">
                    <div v-if="appliedFilters.length > 0" class="filter-list">
                        <div
                            v-for="filter in appliedFilters"
                            :key="filter.id"
                            class="item"
                        >
                            <span class="key">{{ filter.keyLabel }}</span>
                            <span class="comparator">{{ filter.comparatorLabel }}</span>
                            <span class="value">{{ filter.valueLabel }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div>
                <ks-button @click="closeSaveDialog">
                    {{ $t("filter.cancel") }}
                </ks-button>
                <ks-button
                    type="primary"
                    @click="saveFilter"
                    :disabled="!filterName.trim() || hasDuplicate"
                    :icon="ContentSaveOutline"
                >
                    {{ isEditMode ? $t("filter.update") : $t("filter.save") }}
                </ks-button>
            </div>
        </template>
    </ks-dialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue";
    import {AppliedFilter, SavedFilter} from "../utils/filterTypes";
    import {CloseCircleOutline, ContentSaveOutline} from "../utils/icons";

    const props = defineProps<{
        disabled: boolean;
        savedFilters: SavedFilter[];
        editingFilter?: SavedFilter;
        appliedFilters: AppliedFilter[];
    }>();

    const emits = defineEmits<{
        "close-edit": [];
        save: [name: string, description: string];
        edit: [id: string, name: string, description: string];
    }>();

    const filterName = ref("");
    const showSaveDialog = ref(false);
    const filterDescription = ref("");

    const isEditMode = computed(() => !!props.editingFilter);

    const hasDuplicate = computed(() => {
        const name = filterName.value.trim();
        if (!name) return false;
        return props.savedFilters.some(f => f.name === name && (!isEditMode.value || f.id !== props.editingFilter?.id));
    });

    watch(() => props.editingFilter, (newFilter, oldFilter) => {
        if (newFilter && !oldFilter) {
            filterName.value = newFilter.name;
            filterDescription.value = newFilter.description || "";
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
            emits("save", filterName.value.trim(), filterDescription.value.trim());
        }
        closeSaveDialog();
    };

    const closeSaveDialog = () => {
        showSaveDialog.value = false;
        filterName.value = "";
        filterDescription.value = "";
        if (isEditMode.value) {
            emits("close-edit");
        }
    };
</script>

<style lang="scss" scoped>
.save-form {
    >div {
        margin-bottom: 1rem;

        &:last-child {
            margin-bottom: 0;
        }

        label {
            display: block;
            margin-bottom: 0.25rem;
            font-weight: 600;
            font-size: var(--kel-font-size-small);
            color: var(--ks-content-secondary);
        }
    }

    .filter-summary {
        padding: 0.5rem 0.75rem;
        background-color: var(--ks-surface-secondary);
        border-radius: 0.25rem;
        border: 1px solid var(--ks-border-primary);
        min-height: 2rem;
    }

    .filter-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
    }

    .item {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        font-size: var(--kel-font-size-extra-small);

        .key {
            color: var(--ks-content-primary);
            font-weight: 400;
        }

        .comparator {
            color: var(--ks-chart-success);
            font-weight: 400;
        }

        .value {
            color: var(--ks-content-primary);
            font-weight: 700;
        }
    }
}

.no-bg-border {
    margin: 0 !important;
    padding: 0.5rem;
    border-radius: 0.25rem;
    font-size: var(--kel-font-size-base);
    color: var(--ks-content-primary) !important;
    box-shadow: 0 2px 4px var(--ks-card-shadow);
}

.kel-button.is-disabled {
    color: var(--ks-content-tertiary) !important;
    cursor: not-allowed !important;
}

.kel-button-group .kel-button--primary:last-child {
    border: none;
}

:deep(.kel-input__inner::placeholder),
:deep(.kel-textarea__inner::placeholder) {
    color: var(--ks-content-tertiary);
    font-size: var(--kel-font-size-small);
}

:deep(footer.kel-dialog__footer) {
    padding-top: 0 !important;
}
</style>