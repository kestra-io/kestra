<template>
    <div
        class="filter-container"
        :class="{'filter-shrink': filter.searchInputFullWidth.value}"
    >
        <KsButton
            v-if="filter.tableOptions.value?.refresh?.shown"
            @click="filter.refreshData"
            :icon="Refresh"
            :size="'default'"
            class="refresh-button"
        >
            {{ $t("filter.refresh") }}
        </KsButton>

        <KsPopover
            v-if="filter.buttons.value?.savedFilters?.shown !== false"
            v-model:visible="isSavedFiltersVisible"
            placement="bottom-end"
            trigger="click"
            :popperClass="'p-0 saved-filters-popper'"
            :width="300"
            :showArrow="false"
            :disabled="filter.readOnly.value"
            @hide="isSavedFiltersVisible = false"
        >
            <template #reference>
                <KsButton type="default" size="default" class="saved-btn" :icon="BookmarkOutline" :disabled="filter.readOnly.value">
                    <KsTooltip :content="$t('filter.saved tooltip')" placement="top">
                        <span class="saved-content">
                            {{ $t("filter.saved") }}
                            <KsTag type="primary" effect="light" class="saved-count">
                                {{ filter.savedFilters.value.length }}
                            </KsTag>
                            <KsIcon class="el-icon--right">
                                <ChevronDown />
                            </KsIcon>
                        </span>
                    </KsTooltip>
                </KsButton>
            </template>

            <div class="saved-filters-popover">
                <div class="save-current-wrapper">
                    <KsButton
                        type="primary"
                        class="save-current-btn"
                        :icon="ContentSaveOutline"
                        :disabled="saveDisabled"
                        @click="saveFiltersRef?.open()"
                    >
                        {{ $t("filter.save current") }}
                    </KsButton>
                </div>

                <SavedFilters
                    hideHeader
                    :savedFilters="filter.savedFilters.value"
                    @load="handleLoad"
                    @edit="filter.editSavedFilter"
                    @delete="filter.deleteSavedFilter"
                />
            </div>
        </KsPopover>

        <SaveFilters
            v-if="filter.buttons.value?.savedFilters?.shown !== false"
            ref="saveFiltersRef"
            :appliedFilters="filter.appliedFilters.value"
            :editingFilter="filter.editingFilter.value"
            :savedFilters="filter.savedFilters.value"
            @save="handleSave"
            @edit="handleEdit"
            @close-edit="filter.closeEditFilter"
        />

        <KsPopover
            v-if="filter.tableOptions.value?.columns?.shown !== false"
            v-model:visible="isColumnsVisible"
            placement="bottom-end"
            trigger="click"
            :width="300"
            :popperClass="'p-0'"
            :showArrow="false"
            :disabled="filter.readOnly.value"
            @hide="isColumnsVisible = false"
        >
            <template #reference>
                <KsButton
                    type="default"
                    size="default"
                    class="icon-btn"
                    :icon="Table"
                    :aria-label="$t('filter.customize columns')"
                    :title="$t('filter.customize columns')"
                    :disabled="filter.readOnly.value"
                />
            </template>
            <CustomColumns
                :columns="filter.properties.value?.columns ?? []"
                :visibleColumns="filter.properties.value?.displayColumns ?? []"
                :storageKey="filter.properties.value?.storageKey ?? ''"
                @update-columns="filter.updateProperties"
                @close="isColumnsVisible = false"
            />
        </KsPopover>

        <KsPopover
            v-if="filter.buttons.value?.tableOptions?.shown !== false"
            v-model:visible="isSettingsVisible"
            placement="bottom-end"
            trigger="click"
            :width="260"
            :popperClass="'p-0'"
            :showArrow="false"
            @hide="isSettingsVisible = false"
        >
            <template #reference>
                <KsButton
                    type="default"
                    size="default"
                    class="icon-btn"
                    :icon="CogOutline"
                    :aria-label="$t('filter.options')"
                    :title="$t('filter.options')"
                />
            </template>
            <FilterSettings @close="isSettingsVisible = false" />
        </KsPopover>

        <slot name="extra" />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, inject} from "vue"
    import {BookmarkOutline, ChevronDown, CogOutline, ContentSaveOutline, Refresh} from "./utils/icons"
    import Table from "vue-material-design-icons/Table.vue"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    import SaveFilters from "./segments/SaveFilters.vue"
    import SavedFilters from "./segments/SavedFilters.vue"
    import CustomColumns from "./segments/CustomColumns.vue"
    import FilterSettings from "./segments/FilterSettings.vue"

    const isSavedFiltersVisible = ref(false)
    const saveFiltersRef = ref<InstanceType<typeof SaveFilters> | null>(null)
    const isColumnsVisible = ref(false)
    const isSettingsVisible = ref(false)
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const saveDisabled = computed(() =>
        (!filter.hasAppliedFilters.value && !filter.searchQuery.value) || filter.readOnly.value,
    )

    const handleSave = (name: string, description: string) => {
        filter.saveFilter(
            name,
            description,
            filter.appliedFilters.value,
        )
    }

    const handleEdit = (id: string, name: string, description: string) => {
        filter.updateSavedFilter(id, name, description)
    }

    const handleLoad = (savedFilter: any) => {
        filter.loadSavedFilter(savedFilter)
        isSavedFiltersVisible.value = false
    }
</script>

<style lang="scss" scoped>
.filter-container {
    --ks-box-shadow: 0 1px 2px var(--ks-shadow-surface);

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
        font-size: var(--ks-font-size-sm);
        box-shadow: var(--ks-box-shadow);
        background-color: var(--ks-bg-input);

        .saved-content {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .saved-count {
            margin-left: 0.375rem;
            background-color: var(--ks-bg-tag);
            &:hover {
                background-color: var(--ks-bg-tag-hover);
            }
            color: var(--ks-text-secondary);
            border-radius: 0.35rem;
            border: none;
            font-size: 0.625rem;
        }
    }

    .icon-btn {
        box-shadow: var(--ks-box-shadow);
        margin: 0;
        padding: 0.5rem;
        border-radius: var(--ks-radius-base);
        font-size: var(--ks-font-size-base);

        :deep(svg) {
            color: var(--ks-text-dim) !important;
        }
    }

    .refresh-button {
        background-color: transparent;
        border: none;
        box-shadow: none;
        margin: 0;
        padding: 0.25rem 0.5rem;
        font-size: var(--ks-font-size-xs);

        :deep(svg) {
            color: var(--ks-text-dim);
        }

        &:hover {
            background-color: var(--ks-bg-tag);
        }
    }
}

.saved-filters-popover {
    display: flex;
    flex-direction: column;

    .save-current-wrapper {
        padding: var(--ks-spacing-3);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .save-current-btn {
        width: 100%;
        margin: 0;
        justify-content: center;
    }
}

:deep(.bookmark-outline-icon) {
    color: var(--ks-text-muted);
}
</style>

<style lang="scss">
.saved-filters-popper.saved-filters-popper {
    overflow: hidden;
    border-radius: 0.5rem;
    box-shadow: 0px 8px 24px 0px var(--ks-shadow-elevated);
    background-color: var(--ks-bg-elevated);
    border: 1px solid var(--ks-border-strong);
}
</style>
