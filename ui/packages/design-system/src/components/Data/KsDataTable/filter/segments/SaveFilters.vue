<template>
    <KsTooltip v-if="$slots.default" :content="$t('filter.save filter tooltip')" placement="top">
        <KsButton
            type="default"
            :disabled="disabled"
            @click="showSaveDialog = true"
            :icon="ContentSaveOutline"
            class="no-bg-border"
        >
            <slot />
        </KsButton>
    </KsTooltip>

    <KsDialog
        v-model="showSaveDialog"
        :title="isEditMode ? $t('filter.edit filter') : $t('filter.save filter')"
        class="custom-dialog"
        width="25%"
        @close="closeSaveDialog"
    >
        <div class="save-form">
            <KsAlert v-if="hasDuplicate" type="error" :closable="false">
                {{ $t("filter.save duplicate") }}
                <template #icon>
                    <CloseCircleOutline />
                </template>
            </KsAlert>
            <div>
                <label>{{ $t("filter.name.label") }}</label>
                <KsInput
                    v-model="filterName"
                    :placeholder="$t('filter.enter name')"
                    clearable
                />
            </div>

            <div>
                <label>{{ $t("filter.description") }}</label>
                <KsInput
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
                            <span class="comparator">{{ comparatorLabelFor(filter) }}</span>
                            <span class="value">{{ filter.valueLabel }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <template #footer>
            <div>
                <KsButton @click="closeSaveDialog">
                    {{ $t("filter.cancel") }}
                </KsButton>
                <KsButton
                    type="primary"
                    @click="saveFilter"
                    :disabled="!filterName.trim() || hasDuplicate"
                    :icon="ContentSaveOutline"
                >
                    {{ isEditMode ? $t("filter.update") : $t("filter.save") }}
                </KsButton>
            </div>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import type {AppliedFilter, SavedFilter} from "../utils/filterTypes"
    import {isDateRangeValue} from "../utils/filterChipFactory"
    import {CloseCircleOutline, ContentSaveOutline} from "../utils/icons"

    const {t} = useI18n({useScope: "global"})

    // Range filters render a localized "between" label; everything else uses the
    // comparator label baked into the model.
    const comparatorLabelFor = (filter: AppliedFilter): string =>
        isDateRangeValue(filter.value) ? t("filter.is_between") : filter.comparatorLabel

    const props = defineProps<{
        savedFilters: SavedFilter[];
        editingFilter?: SavedFilter;
        appliedFilters: AppliedFilter[];
        disabled?: boolean;
    }>()

    const emits = defineEmits<{
        "close-edit": [];
        save: [name: string, description: string];
        edit: [id: string, name: string, description: string];
    }>()

    const filterName = ref("")
    const showSaveDialog = ref(false)
    const filterDescription = ref("")

    defineExpose({open: () => { showSaveDialog.value = true }})

    const isEditMode = computed(() => !!props.editingFilter)

    const hasDuplicate = computed(() => {
        const name = filterName.value.trim()
        if (!name) return false
        return props.savedFilters.some(f => f.name === name && (!isEditMode.value || f.id !== props.editingFilter?.id))
    })

    watch(() => props.editingFilter, (newFilter, oldFilter) => {
        if (newFilter && !oldFilter) {
            filterName.value = newFilter.name
            filterDescription.value = newFilter.description || ""
            showSaveDialog.value = true
        } else if (!newFilter && oldFilter) {
            closeSaveDialog()
        }
    }, {immediate: true})

    const saveFilter = () => {
        if (!filterName.value.trim()) return

        if (isEditMode.value && props.editingFilter) {
            emits("edit", props.editingFilter.id, filterName.value.trim(), filterDescription.value.trim())
        } else {
            emits("save", filterName.value.trim(), filterDescription.value.trim())
        }
        closeSaveDialog()
    }

    const closeSaveDialog = () => {
        showSaveDialog.value = false
        filterName.value = ""
        filterDescription.value = ""
        if (isEditMode.value) {
            emits("close-edit")
        }
    }
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
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-secondary);
        }
    }

    .filter-summary {
        padding: 0.5rem 0.75rem;
        background-color: var(--ks-surface-secondary);
        border-radius: var(--ks-radius-base);
        border: 1px solid var(--ks-border-default);
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
        font-size: var(--ks-font-size-xs);

        .key {
            color: var(--ks-text-primary);
            font-weight: 400;
        }

        .comparator {
            color: var(--ks-status-success);
            font-weight: 400;
        }

        .value {
            color: var(--ks-text-primary);
            font-weight: 700;
        }
    }
}

.kel-button.is-disabled {
    color: var(--ks-text-dim) !important;
    cursor: not-allowed !important;
}

.kel-button-group .kel-button--primary:last-child {
    border: none;
}

:deep(.kel-input__inner::placeholder),
:deep(.kel-textarea__inner::placeholder) {
    color: var(--ks-text-dim);
    font-size: var(--ks-font-size-sm);
}

:deep(footer.kel-dialog__footer) {
    padding-top: 0 !important;
}
</style>