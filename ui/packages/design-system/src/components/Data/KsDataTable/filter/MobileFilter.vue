<template>
    <div class="mobile-filter">
        <div class="mobile-bar">
            <div
                v-if="filter.showSearchInput?.value"
                class="mobile-search"
                :class="{'read-only': filter.readOnly?.value}"
            >
                <KsSearch
                    v-model="localSearchQuery"
                    @update:modelValue="(v) => debouncedUpdateSearch(v ?? '')"
                    :placeholder="filter.configuration?.value?.searchPlaceholder"
                    clearable
                />
            </div>

            <KsButton
                v-if="filter.hasFilterKeys?.value"
                :icon="Tune"
                size="default"
                class="mobile-toggle"
                :class="{'mobile-toggle--active': open}"
                :disabled="filter.readOnly?.value"
                :aria-label="$t('filter.mobile_filters')"
                @click="open = !open"
            >
                <span v-if="activeCount" class="mobile-toggle-count">{{ activeCount }}</span>
            </KsButton>

            <slot name="extra" />
        </div>

        <Transition name="mobile-sheet-fade">
            <div v-if="open" class="mobile-scrim" @click="open = false" />
        </Transition>

        <Transition name="mobile-sheet-slide">
            <div
                v-if="open"
                class="mobile-sheet"
                role="dialog"
                aria-modal="true"
                :aria-label="$t('filter.mobile_filters')"
            >
                <button
                    type="button"
                    class="sheet-section-header"
                    @click="sectionExpanded = !sectionExpanded"
                >
                    <KsIcon class="sheet-section-icon"><FilterOutline /></KsIcon>
                    <span class="sheet-section-title">{{ $t("filter.filters_title") }}</span>
                    <span v-if="activeCount" class="count-badge">{{ activeCount }}</span>
                    <KsIcon class="sheet-chevron" :class="{'sheet-chevron--open': sectionExpanded}">
                        <ChevronDown />
                    </KsIcon>
                </button>

                <div v-show="sectionExpanded" class="sheet-section-body">
                    <KsSegmented
                        v-model="tab"
                        :options="tabOptions"
                        block
                        class="sheet-tabs"
                    />

                    <div v-if="tab === TAB_ALL" class="field-list">
                        <div
                            v-for="key in filterableKeys"
                            :key="key.key"
                            class="field-row"
                            :class="{'field-row--open': expandedKey === key.key}"
                        >
                            <button
                                type="button"
                                class="field-row-header"
                                :disabled="filter.readOnly?.value"
                                @click="toggleField(key)"
                            >
                                <span class="field-row-label">{{ key.label }}</span>
                                <span v-if="countFor(key)" class="count-badge">{{ countFor(key) }}</span>
                                <KsIcon
                                    class="field-chevron"
                                    :class="{'field-chevron--open': expandedKey === key.key}"
                                >
                                    <ChevronDown />
                                </KsIcon>
                            </button>

                            <div v-if="expandedKey === key.key && editTarget" class="field-row-body">
                                <FilterEditPopper
                                    :key="editTarget.id"
                                    :filter="editTarget"
                                    :filterKey="key"
                                    :showComparatorSelection="(key.comparators?.length ?? 0) >= 2"
                                    @update="commitField"
                                    @remove="removeField"
                                    @close="collapseField"
                                />
                            </div>
                        </div>
                    </div>

                    <SavedFilters
                        v-else
                        hideHeader
                        :savedFilters="filter.savedFilters?.value ?? []"
                        @load="onLoadSaved"
                        @edit="filter.editSavedFilter"
                        @delete="filter.deleteSavedFilter"
                        @close="open = false"
                    />
                </div>

                <div class="sheet-footer">
                    <KsButton
                        link
                        class="sheet-clear"
                        :disabled="!canReset || filter.readOnly?.value"
                        @click="filter.clearFilters"
                    >
                        {{ $t("filter.reset") }}
                    </KsButton>

                    <div class="sheet-footer-actions">
                        <SaveFilters
                            :disabled="(!filter.hasAppliedFilters?.value && !filter.searchQuery?.value) || filter.readOnly?.value"
                            :appliedFilters="filter.appliedFilters?.value ?? []"
                            :editingFilter="filter.editingFilter?.value"
                            :savedFilters="filter.savedFilters?.value ?? []"
                            @save="onSaveFilter"
                            @edit="filter.updateSavedFilter"
                            @close-edit="filter.closeEditFilter"
                        >
                            {{ $t("filter.save") }}
                        </SaveFilters>

                        <KsButton type="primary" size="default" class="sheet-apply" @click="open = false">
                            {{ $t("filter.apply") }}
                        </KsButton>
                    </div>
                </div>
            </div>
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, onBeforeUnmount, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useDebounceFn} from "@vueuse/core"

    import FilterEditPopper from "./layout/FilterEditPopper.vue"
    import SavedFilters from "./segments/SavedFilters.vue"
    import SaveFilters from "./segments/SaveFilters.vue"
    import {buildNewFilter} from "./utils/filterChipFactory"
    import {ChevronDown, FilterOutline, Tune} from "./utils/icons"
    import {type AppliedFilter, type FilterKeyConfig, type SavedFilter} from "./utils/filterTypes"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    const {t} = useI18n({useScope: "global"})

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const TAB_ALL = "all"
    const TAB_SAVED = "saved"

    const open = ref(false)
    const sectionExpanded = ref(true)
    const tab = ref(TAB_ALL)
    const expandedKey = ref<string | null>(null)
    const editTarget = ref<AppliedFilter | null>(null)

    const tabOptions = computed(() => [
        {label: t("filter.all_filters"), value: TAB_ALL},
        {label: t("filter.my_filters_saved"), value: TAB_SAVED},
    ])

    const filterableKeys = computed<FilterKeyConfig[]>(
        () => filter.configuration?.value?.keys ?? [],
    )

    const hasValue = (value: AppliedFilter["value"]): boolean => {
        if (Array.isArray(value)) return value.length > 0
        if (value == null) return false
        if (typeof value === "string") return value !== ""
        return true
    }

    const appliedFor = (key: FilterKeyConfig): AppliedFilter | undefined =>
        (filter.appliedFilters?.value ?? []).find((f) => f.key === key.key)

    const countFor = (key: FilterKeyConfig): number => {
        const applied = appliedFor(key)
        if (!applied || !hasValue(applied.value)) return 0
        return Array.isArray(applied.value) ? applied.value.length : 1
    }

    const activeCount = computed(
        () => (filter.appliedFilters?.value ?? []).filter((f) => hasValue(f.value)).length,
    )

    const canReset = computed(
        () => !!filter.hasAppliedFilters?.value
            || !!filter.hasDismissedDefaultVisibleKeys?.value
            || !!filter.searchQuery?.value,
    )

    const collapseField = () => {
        expandedKey.value = null
        editTarget.value = null
    }

    const toggleField = (key: FilterKeyConfig) => {
        if (expandedKey.value === key.key) {
            collapseField()
            return
        }
        const target = appliedFor(key) ?? buildNewFilter(key)
        if (!target) return
        editTarget.value = target
        expandedKey.value = key.key
    }

    const commitField = (updated: AppliedFilter) => {
        const exists = (filter.appliedFilters?.value ?? []).some((f) => f.id === updated.id)
        if (exists) filter.updateFilter(updated)
        else filter.addFilter(updated)
        collapseField()
    }

    const removeField = (id: string) => {
        filter.removeFilter(id)
        collapseField()
    }

    const onLoadSaved = (savedFilter: SavedFilter) => {
        filter.loadSavedFilter(savedFilter)
        tab.value = TAB_ALL
    }

    const onSaveFilter = (name: string, description: string) => {
        filter.saveFilter(name, description, filter.appliedFilters?.value ?? [])
    }

    const localSearchQuery = ref(filter.searchQuery?.value ?? "")
    watch(() => filter.searchQuery?.value, (v) => {
        if (v !== localSearchQuery.value) localSearchQuery.value = v ?? ""
    })
    const debouncedUpdateSearch = useDebounceFn((value: string) => {
        filter.searchQuery.value = value
    }, 700)

    const handleKeydown = (e: KeyboardEvent) => {
        if (e.key === "Escape" && open.value) {
            e.stopPropagation()
            open.value = false
        }
    }
    watch(open, (visible) => {
        if (visible) {
            window.addEventListener("keydown", handleKeydown)
        } else {
            collapseField()
            window.removeEventListener("keydown", handleKeydown)
        }
    })
    onBeforeUnmount(() => window.removeEventListener("keydown", handleKeydown))
</script>

<style lang="scss" scoped>
.mobile-filter {
    position: relative;
    width: 100%;
}

.mobile-bar {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
}

.mobile-search {
    flex: 1;
    min-width: 0;

    :deep(.kel-input__wrapper) {
        height: 32px;
        border-radius: var(--ks-radius-base);
    }

    &.read-only {
        pointer-events: none;
        opacity: 0.6;
    }
}

.mobile-toggle {
    position: relative;
    margin: 0 !important;
    flex-shrink: 0;
    background-color: var(--ks-btn-secondary-bg-default);
    box-shadow: 0 1px 2px var(--ks-shadow-surface);

    :deep(svg) {
        color: var(--ks-text-dim) !important;
        font-size: var(--ks-font-size-md);
    }

    &:hover {
        background-color: var(--ks-btn-secondary-bg-hover);
    }

    &--active {
        background-color: var(--ks-btn-secondary-bg-active);
        border-color: var(--ks-btn-secondary-border-active);

        :deep(svg) {
            color: var(--ks-text-link) !important;
        }
    }

    .mobile-toggle-count {
        position: absolute;
        top: -6px;
        right: -6px;
        min-width: 16px;
        height: 16px;
        padding: 0 4px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 999px;
        background-color: var(--ks-btn-primary-bg-default);
        color: var(--ks-btn-primary-text);
        font-size: 10px;
        font-weight: 600;
        line-height: 1;
    }
}

.mobile-scrim {
    position: fixed;
    inset: 0;
    z-index: 1400;
    background-color: transparent;
}

.mobile-sheet {
    position: absolute;
    top: calc(100% + var(--ks-spacing-2));
    left: 0;
    right: 0;
    z-index: 1401;
    display: flex;
    flex-direction: column;
    max-height: 75vh;
    overflow-y: auto;
    background-color: var(--ks-bg-surface);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-lg);
    box-shadow: 0 8px 24px var(--ks-shadow-elevated);
}

.sheet-section-header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border: none;
    background: none;
    cursor: pointer;
    font-family: inherit;

    .sheet-section-icon :deep(svg) {
        color: var(--ks-icon-muted);
        font-size: var(--ks-font-size-md);
    }

    .sheet-section-title {
        flex: 1;
        text-align: left;
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .sheet-chevron {
        :deep(svg) {
            color: var(--ks-icon-muted);
            font-size: var(--ks-font-size-md);
            transition: transform 150ms ease;
        }

        &--open :deep(svg) {
            transform: rotate(180deg);
        }
    }
}

.sheet-section-body {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
    padding: 0 var(--ks-spacing-4) var(--ks-spacing-3);
}

.sheet-tabs {
    width: 100%;
}

.field-list {
    display: flex;
    flex-direction: column;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    overflow: hidden;
}

.field-row {
    border-bottom: 1px solid var(--ks-border-default);

    &:last-child {
        border-bottom: none;
    }

    &--open {
        background-color: var(--ks-bg-base);
    }
}

.field-row-header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border: none;
    background: none;
    cursor: pointer;
    font-family: inherit;

    &:disabled {
        cursor: default;
        opacity: 0.6;
    }

    .field-row-label {
        flex: 1;
        text-align: left;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .field-chevron :deep(svg) {
        color: var(--ks-icon-muted);
        font-size: var(--ks-font-size-md);
        transition: transform 150ms ease;
    }

    .field-chevron--open :deep(svg) {
        transform: rotate(180deg);
    }
}

.field-row-body {
    padding: 0 var(--ks-spacing-2) var(--ks-spacing-2);

    :deep(.edit-popper) {
        background: none;
    }
}

.count-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 999px;
    background-color: var(--ks-bg-badge);
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    line-height: 1;
}

.sheet-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-top: 1px solid var(--ks-border-default);
    position: sticky;
    bottom: 0;
    background-color: var(--ks-bg-surface);
}

.sheet-clear {
    margin: 0 !important;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);

    &:hover {
        color: var(--ks-text-primary);
    }
}

.sheet-footer-actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
}

.sheet-apply {
    margin: 0 !important;
}

.mobile-sheet-fade-enter-active,
.mobile-sheet-fade-leave-active {
    transition: opacity 150ms ease;
}

.mobile-sheet-fade-enter-from,
.mobile-sheet-fade-leave-to {
    opacity: 0;
}

.mobile-sheet-slide-enter-active,
.mobile-sheet-slide-leave-active {
    transition: opacity 150ms ease, transform 150ms ease;
}

.mobile-sheet-slide-enter-from,
.mobile-sheet-slide-leave-to {
    opacity: 0;
    transform: translateY(-8px);
}
</style>
