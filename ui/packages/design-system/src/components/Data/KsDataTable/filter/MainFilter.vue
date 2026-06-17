<template>
    <div ref="containerRef" class="filter-container" :class="{'filter-grow': filter.searchInputFullWidth?.value}">
        <KsPopover
            v-if="filter.hasFilterKeys?.value"
            v-model:visible="isCustomizeFiltersVisible"
            placement="bottom-start"
            trigger="click"
            :width="300"
            :popperClass="'p-0'"
            :showArrow="false"
            :disabled="filter.readOnly?.value || isComplex"
            @hide="isCustomizeFiltersVisible = false"
        >
            <template #reference>
                <KsButton
                    :icon="FilterOutline"
                    size="default"
                    class="customize-button"
                    :disabled="filter.readOnly?.value"
                    @click="onCustomizeClick"
                >
                    <KsTooltip
                        placement="top"
                        :content="$t('filter.customize tooltip')"
                        :disabled="filter.readOnly?.value"
                    >
                        <span>{{ $t("filter.customize") }}</span>
                    </KsTooltip>
                </KsButton>
            </template>

            <CustomizeFilters
                :configuration="filter.configuration?.value"
                :appliedFilters="filter.appliedFilters?.value"
                @add-filter="handleAddFilter"
                @remove-filter="filter.removeFilter"
                @open-advanced="openAdvanced('.customize-button')"
                @close="isCustomizeFiltersVisible = false"
            />
        </KsPopover>

        <div
            v-if="filter.showSearchInput?.value"
            class="search-container"
            :class="{
                'search-grow': filter.searchInputFullWidth?.value,
                'read-only': filter.readOnly?.value
            }"
        >
            <KsSearch
                v-model="localSearchQuery"
                @update:modelValue="(v) => debouncedUpdateSearch(v ?? '')"
                :placeholder="filter.configuration?.value?.searchPlaceholder"
                clearable
            />
        </div>

        <KsButton
            v-if="isComplex"
            size="default"
            class="rules-pill"
            :disabled="filter.readOnly?.value"
            @click="openAdvanced('.rules-pill')"
        >
            <span class="rules-pill-content">
                <KsIcon class="rules-pill-icon"><FilterVariant /></KsIcon>
                <span class="rules-pill-label">{{ ruleLabel }}</span>
                <KsIcon class="rules-pill-chevron"><ChevronDown /></KsIcon>
            </span>
            <span class="rules-pill-dot" />
        </KsButton>

        <div
            v-for="cf in conditionalFilters"
            v-else
            :key="cf.id"
            class="filter-chip-wrap"
        >
            <FilterChip
                :ref="(el: any) => setChipRef(cf.id, el)"
                :filter="cf"
                :filterKey="keyConfigFor(cf)"
                :class="{'read-only': filter.readOnly?.value}"
                class="filter-chip"
                @remove="filter.removeFilter"
                @update="filter.updateFilter"
            />
        </div>

        <template v-if="globalFilters.length || (unappliedGlobalKeys.length && !filter.readOnly?.value)">
            <div
                ref="globalFiltersRef"
                class="global-filters"
                :class="{'is-wrapped': globalWrapOffset > 0}"
                :style="globalWrapOffset > 0 ? {marginLeft: `-${globalWrapOffset}px`} : undefined"
            >
                <div
                    v-for="gf in globalFilters"
                    :key="gf.id"
                    class="filter-chip-wrap"
                >
                    <FilterChip
                        :ref="(el: any) => setChipRef(gf.id, el)"
                        :filter="gf"
                        :filterKey="keyConfigFor(gf)"
                        :class="{'read-only': filter.readOnly?.value}"
                        class="filter-chip"
                        @remove="filter.removeFilter"
                        @update="filter.updateFilter"
                    />
                </div>
                <template v-if="!filter.readOnly?.value">
                    <KsButton
                        v-for="key in unappliedGlobalKeys"
                        :key="`add-${key.key}`"
                        :icon="Plus"
                        size="default"
                        class="add-global-btn"
                        @click="addGlobalFilter(key)"
                    >
                        {{ key.label }}
                    </KsButton>
                </template>
            </div>
        </template>

        <KsTooltip
            v-if="filter.hasFilterKeys?.value"
            placement="top"
            :content="$t('filter.reset_all')"
            :disabled="filter.readOnly?.value"
        >
            <KsButton
                link
                class="refresh-btn"
                @click="handleReset"
                :disabled="!canReset || filter.readOnly?.value"
            >
                {{ $t("filter.reset") }}
            </KsButton>
        </KsTooltip>

        <AdvancedFilterBuilder v-model="isAdvancedOpen" :anchor="advancedAnchor" />
    </div>
</template>

<script setup lang="ts">
    import {ref, inject, nextTick, computed, watch, onMounted, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import {useDebounceFn} from "@vueuse/core"

    import {ChevronDown, FilterOutline, FilterVariant, Plus} from "./utils/icons"

    import CustomizeFilters from "./segments/CustomizeFilters.vue"
    import AdvancedFilterBuilder from "./AdvancedFilterBuilder.vue"
    import FilterChip from "./layout/FilterChip.vue"

    import {buildNewFilter} from "./utils/filterChipFactory"
    import {type AppliedFilter, type FilterKeyConfig} from "./utils/filterTypes"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    const {t} = useI18n({useScope: "global"})

    const isCustomizeFiltersVisible = ref(false)
    const isAdvancedOpen = ref(false)
    const advancedAnchor = ref(".customize-button")
    const chipRefs = ref<Record<string, any>>({})
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const containerRef = ref<HTMLElement | null>(null)
    const globalFiltersRef = ref<HTMLElement | null>(null)
    const globalWrapOffset = ref(0)

    const openAdvanced = (anchor = ".customize-button") => {
        advancedAnchor.value = anchor
        isCustomizeFiltersVisible.value = false
        isAdvancedOpen.value = true
    }

    const keyConfigFor = (appliedFilter: AppliedFilter): FilterKeyConfig | null =>
        filter.configuration?.value?.keys?.find((key) => key.key === appliedFilter.key) ?? null

    const isGlobal = (appliedFilter: AppliedFilter): boolean =>
        keyConfigFor(appliedFilter)?.groupable === false

    const globalFilters = computed(() =>
        (filter.appliedFilters?.value ?? []).filter(isGlobal),
    )

    const conditionalFilters = computed(() =>
        (filter.appliedFilters?.value ?? []).filter((f) => !isGlobal(f)),
    )

    const unappliedGlobalKeys = computed(() =>
        (filter.configuration?.value?.keys ?? [])
            .filter((key: FilterKeyConfig) => key.groupable === false)
            .filter((key: FilterKeyConfig) => !globalFilters.value.some((f: AppliedFilter) => f.key === key.key)),
    )

    const addGlobalFilter = (key: FilterKeyConfig) => {
        const newFilter = buildNewFilter(key)
        if (!newFilter) return
        filter.addFilter(newFilter)
        nextTick(() => chipRefs.value[newFilter.id]?.editPopover?.toggleDialog())
    }

    const setChipRef = (filterId: string, el: any) => el
        ? chipRefs.value[filterId] = el
        : delete chipRefs.value[filterId]

    const hasValue = (value: AppliedFilter["value"]): boolean => {
        if (Array.isArray(value)) return value.length > 0
        if (value == null) return false
        if (typeof value === "string") return value !== ""
        return true
    }

    const ruleCount = computed(() =>
        conditionalFilters.value.filter((f) => hasValue(f.value)).length,
    )

    const isComplex = computed(() => {
        const groups = filter.groups?.value ?? []
        return ruleCount.value > 1
            || groups.length > 1
            || groups.some((group) => group.kind === "wrapper")
    })

    const onCustomizeClick = () => {
        if (isComplex.value) openAdvanced(".customize-button")
    }

    const ruleLabel = computed(() =>
        ruleCount.value === 1
            ? t("filter.rule_count", {count: ruleCount.value})
            : t("filter.rules_count", {count: ruleCount.value}),
    )

    const canReset = computed(() => {
        return (
            !!filter.hasAppliedFilters?.value ||
            !!filter.hasDismissedDefaultVisibleKeys?.value ||
            !!filter.searchQuery?.value
        )
    })

    const handleAddFilter = (newFilter: AppliedFilter) => {
        filter.addFilter(newFilter)
        isCustomizeFiltersVisible.value = false
        nextTick(() => {
            if (isComplex.value) openAdvanced(".customize-button")
            else chipRefs.value[newFilter.id]?.editPopover?.toggleDialog()
        })
    }

    const handleReset = () => {
        filter.clearFilters()
    }

    const localSearchQuery = ref(filter.searchQuery?.value ?? "")
    watch(() => filter.searchQuery?.value, (v) => {
        if (v !== localSearchQuery.value) localSearchQuery.value = v ?? ""
    })

    const debouncedUpdateSearch = useDebounceFn((value: string) => {
        filter.searchQuery.value = value
    }, 700)

    const measureGlobalWrap = () => {
        const container = containerRef.value
        const globalFiltersEl = globalFiltersRef.value
        if (!container || !globalFiltersEl) {
            globalWrapOffset.value = 0
            return
        }
        const containerRect = container.getBoundingClientRect()
        const isWrappedBelowFirstRow = globalFiltersEl.getBoundingClientRect().top - containerRect.top > 6
        if (!isWrappedBelowFirstRow) {
            globalWrapOffset.value = 0
            return
        }
        const barLeft = container.parentElement?.getBoundingClientRect().left ?? containerRect.left
        globalWrapOffset.value = Math.max(0, Math.round(containerRect.left - barLeft))
    }

    let wrapObserver: ResizeObserver | undefined
    onMounted(() => {
        if (containerRef.value && typeof ResizeObserver !== "undefined") {
            wrapObserver = new ResizeObserver(() => measureGlobalWrap())
            wrapObserver.observe(containerRef.value)
        }
        nextTick(measureGlobalWrap)
    })
    onBeforeUnmount(() => wrapObserver?.disconnect())
    watch([globalFilters, conditionalFilters, isComplex], () => nextTick(measureGlobalWrap))
</script>

<style lang="scss" scoped>
.filter-container {
    --ks-box-shadow: 0 1px 2px var(--ks-shadow-surface);

    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    flex: 1;
    min-width: 7rem;

    &.filter-grow {
        flex-wrap: nowrap;
        flex-grow: 1;
    }
}

.filter-chip-wrap {
    flex-shrink: 0;
}

.filter-chip {
    flex-shrink: 0;
    box-shadow: var(--ks-box-shadow);

    &.read-only {
        pointer-events: none;
        opacity: 0.6;
    }
}

.global-filters {
    display: inline-flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    padding-left: var(--ks-spacing-2);
    border-left: 1px solid var(--ks-border-default);
    min-height: 1.75rem;

    &.is-wrapped {
        padding-left: 0;
        border-left: none;
    }
}

.add-global-btn {
    margin: 0 !important;
    flex-shrink: 0;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    border: 1px dashed var(--ks-border-default);
    background: transparent;

    &:hover {
        color: var(--ks-text-primary);
        background: var(--ks-bg-hover);
    }
}

.rules-pill {
    position: relative;
    margin: 0 !important;
    background-color: var(--ks-btn-secondary-bg-default);
    font-size: var(--ks-font-size-xs);
    flex-shrink: 0;
    box-shadow: var(--ks-box-shadow);

    &:hover {
        background-color: var(--ks-btn-secondary-bg-hover);
    }

    .rules-pill-content {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .rules-pill-label {
        color: var(--ks-content-link, var(--ks-text-link));
        font-weight: 600;
    }

    :deep(.rules-pill-icon svg) {
        color: var(--ks-content-link, var(--ks-text-link)) !important;
        font-size: var(--ks-font-size-md);
    }

    :deep(.rules-pill-chevron svg) {
        color: var(--ks-text-dim) !important;
        font-size: var(--ks-font-size-md);
    }

    .rules-pill-dot {
        position: absolute;
        top: -2px;
        right: -2px;
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background-color: var(--ks-status-warning);
    }
}

.customize-button {
    margin: 0 !important;
    background-color: var(--ks-btn-secondary-bg-default);
    font-size: var(--ks-font-size-xs);
    flex-shrink: 0;
    box-shadow: var(--ks-box-shadow);

    &:hover {
        background-color: var(--ks-btn-secondary-bg-hover);
    }

    :deep(svg) {
        color: var(--ks-text-dim) !important;
        font-size: var(--ks-font-size-md);
    }
}

.refresh-btn {
    margin: 0 !important;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);

    &:hover {
        color: var(--ks-text-primary);
        text-decoration: underline;
    }
}

.search-container {
    position: relative;
    flex: 0 0 200px;
    min-width: 150px;
    max-width: 200px;

    :deep(.kel-input__wrapper),
    :deep(.kel-input__inner) {
        border-radius: var(--ks-radius-base);
    }

    :deep(.kel-input__wrapper) {
        height: 32px;
    }

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

</style>
