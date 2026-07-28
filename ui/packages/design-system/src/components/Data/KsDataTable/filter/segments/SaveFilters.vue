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

            <div>
                <p v-if="isEditMode" class="update-hint">{{ $t("filter.update conditions hint") }}</p>
                <div class="filter-summary">
                    <div v-if="isSimpleFlat && appliedFilters.length > 0" class="filter-list">
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
                    <div v-else-if="!isSimpleFlat" class="filter-groups">
                        <template v-for="(box, boxIndex) in previewGroups" :key="box.id">
                            <div v-if="boxIndex > 0" class="group-operator">{{ logicalLabel(topLogical) }}</div>
                            <div class="filter-group-box">
                                <template v-for="(leaf, leafIndex) in box.leaves" :key="leaf.id">
                                    <div v-if="leafIndex > 0" class="leaf-operator">{{ logicalLabel(box.logical) }}</div>
                                    <div class="filter-list">
                                        <div
                                            v-for="filter in leaf.filters"
                                            :key="filter.id"
                                            class="item"
                                        >
                                            <span class="key">{{ filter.keyLabel }}</span>
                                            <span class="comparator">{{ comparatorLabelFor(filter) }}</span>
                                            <span class="value">{{ filter.valueLabel }}</span>
                                        </div>
                                    </div>
                                </template>
                            </div>
                        </template>
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
    import type {AppliedFilter, FilterGroup, LogicalOperator, SavedFilter} from "../utils/filterTypes"
    import {isWrapperGroup} from "../utils/filterTypes"
    import {isDateRangeValue} from "../utils/filterChipFactory"
    import {CloseCircleOutline, ContentSaveOutline} from "../utils/icons"

    interface PreviewLeaf {
        id: string;
        filters: AppliedFilter[];
    }

    interface PreviewBox {
        id: string;
        logical: LogicalOperator;
        leaves: PreviewLeaf[];
    }

    const {t} = useI18n({useScope: "global"})

    // Range filters render a localized "between" label; everything else uses the
    // comparator label baked into the model.
    const comparatorLabelFor = (filter: AppliedFilter): string =>
        isDateRangeValue(filter.value) ? t("filter.is_between") : filter.comparatorLabel

    const logicalLabel = (op: LogicalOperator): string => t(op === "OR" ? "filter.or" : "filter.and")

    const props = withDefaults(defineProps<{
        savedFilters: SavedFilter[];
        editingFilter?: SavedFilter;
        appliedFilters: AppliedFilter[];
        groups?: FilterGroup[];
        topLogical?: LogicalOperator;
        disabled?: boolean;
    }>(), {
        topLogical: "OR",
    })

    // A single, ungrouped condition set (the common case) keeps the original flat preview —
    // boxes and AND/OR labels only appear once there's real nesting to show.
    const isSimpleFlat = computed(() =>
        !props.groups || props.groups.length === 0 || (props.groups.length === 1 && !isWrapperGroup(props.groups[0])),
    )

    const previewGroups = computed<PreviewBox[]>(() => {
        if (!props.groups) return []
        return props.groups
            .map((unit): PreviewBox | null => {
                if (isWrapperGroup(unit)) {
                    const leaves = unit.children
                        .filter((child) => child.filters.length > 0)
                        .map((child) => ({id: child.id, filters: child.filters}))
                    return leaves.length > 0 ? {id: unit.id, logical: unit.logical, leaves} : null
                }
                return unit.filters.length > 0
                    ? {id: unit.id, logical: "AND", leaves: [{id: unit.id, filters: unit.filters}]}
                    : null
            })
            .filter((box): box is PreviewBox => box !== null)
    })

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

    .update-hint {
        margin: 0 0 0.5rem;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
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

    .filter-groups {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .filter-group-box {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
    }

    .group-operator,
    .leaf-operator {
        align-self: flex-start;
        padding: 0 var(--ks-spacing-1);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-xs);
        background-color: var(--ks-bg-elevated);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        color: var(--ks-text-secondary);
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