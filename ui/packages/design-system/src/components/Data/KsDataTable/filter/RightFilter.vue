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

        <SaveFilters
            v-if="!filter.searchInputFullWidth.value && filter.buttons.value?.savedFilters?.shown !== false"
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

        <KsPopover
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
                <KsButton type="default" size="default" class="saved-btn" :icon="BookmarkCheckOutline" :disabled="filter.readOnly.value">
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

            <SavedFilters
                :savedFilters="filter.savedFilters.value"
                @load="handleLoad"
                @edit="filter.editSavedFilter"
                @delete="filter.deleteSavedFilter"
                @close="isSavedFiltersVisible = false"
            />
        </KsPopover>

        <KsTooltip
            v-if="filter.buttons.value?.tableOptions?.shown !== false"
            :content="$t('filter.show data options tooltip')"
            placement="top"
        >
            <KsButton
                type="default"
                size="default"
                @click="filter.toggleOptions"
                class="options-btn"
                :icon="VerticalSliders"
            />
        </KsTooltip>

        <slot name="extra" />
    </div>
</template>

<script setup lang="ts">
    import {ref, inject} from "vue"
    import {ChevronDown, BookmarkCheckOutline, Refresh} from "./utils/icons"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    import SaveFilters from "./segments/SaveFilters.vue"
    import SavedFilters from "./segments/SavedFilters.vue"
    import VerticalSliders from "./assets/VerticalSliders.vue"

    const isSavedFiltersVisible = ref(false)
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

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
        font-size: var(--ks-font-size-sm);
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
        font-size: var(--ks-font-size-base);
        color: var(--ks-content-primary) !important;
    }

    .refresh-button {
        background-color: transparent;
        border: none;
        box-shadow: none;
        margin: 0;
        padding: 0.25rem 0.5rem;
        font-size: var(--ks-font-size-xs);

        :deep(svg) {
            color: var(--ks-content-tertiary);
        }

        &:hover {
            background-color: var(--ks-tag-background);
        }
    }
}
</style>
