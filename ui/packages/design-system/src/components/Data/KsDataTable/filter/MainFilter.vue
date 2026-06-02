<template>
    <div class="filter-container" :class="{'filter-grow': filter.searchInputFullWidth?.value}">
        <KsPopover
            v-if="filter.hasFilterKeys?.value"
            v-model:visible="isCustomizeFiltersVisible"
            placement="bottom-start"
            trigger="click"
            :width="300"
            :popperClass="'p-0'"
            :showArrow="false"
            :disabled="filter.readOnly?.value"
            @hide="isCustomizeFiltersVisible = false"
        >
            <template #reference>
                <KsButton
                    :icon="FilterOutline"
                    size="default"
                    class="customize-button"
                    :disabled="filter.readOnly?.value"
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
                @close="isCustomizeFiltersVisible = false"
                @drag-end-field="onFieldDragEnd"
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

        <template v-for="(unit, unitIndex) in filter.groups?.value" :key="unit.id">
            <LogicalSeparator
                v-if="unitIndex > 0"
                :logical="filter.topLogical?.value"
                :disabled="filter.readOnly?.value"
                :hidden="filter.searchInputFullWidth?.value"
                :linked="hoveredTopSeparator"
                @change="filter.setTopLogical"
                @mouseenter="hoveredTopSeparator = true"
                @mouseleave="hoveredTopSeparator = false"
            />
            <FilterGroupRenderer
                :unit="unit"
                :totalUnits="filter.groups?.value.length ?? 0"
                :dragOverGroupId="dragOverGroupId"
                :draggingEntity="draggingEntity"
                :setChipRef="setChipRef"
                @drag-start-filter="onChipDragStart"
                @drag-start-group="onGroupDragStart"
                @drag-end="onDragEnd"
                @drag-over="onDragOver"
                @drag-leave="onDragLeave"
                @drop="onDrop"
            />
        </template>

        <KsTooltip
            v-if="filter.hasFilterKeys?.value && !filter.readOnly?.value"
            placement="top"
            :content="$t('filter.add_condition_group_tooltip')"
        >
            <KsButton
                link
                class="add-group-btn"
                :class="{'filters-hidden': filter.searchInputFullWidth?.value}"
                @click="filter.addGroup"
            >
                {{ $t("filter.add_condition_group") }}
            </KsButton>
        </KsTooltip>

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
    </div>
</template>

<script setup lang="ts">
    import {ref, inject, nextTick, computed, watch} from "vue"
    import {useDebounceFn} from "@vueuse/core"

    import {FilterOutline} from "./utils/icons"

    import CustomizeFilters from "./segments/CustomizeFilters.vue"
    import LogicalSeparator from "./segments/LogicalSeparator.vue"
    import FilterGroupRenderer from "./FilterGroupRenderer.vue"

    import {COMPARATOR_LABELS, type AppliedFilter} from "./utils/filterTypes"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"

    const isCustomizeFiltersVisible = ref(false)
    const chipRefs = ref<Record<string, any>>({})
    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const hoveredTopSeparator = ref(false)

    type DragKind = "filter" | "group" | "field"
    interface DragEntity { kind: DragKind; id: string }

    /** Entity currently being dragged — chip or group. */
    const draggingEntity = ref<DragEntity | null>(null)
    /** Group or wrapper the drag is hovering over. Used to highlight the active drop target. */
    const dragOverGroupId = ref<string | null>(null)

    const ENTITY_MIME = "application/x-kestra-filter-entity"

    const parseEntity = (raw: string | undefined | null): DragEntity | null => {
        if (!raw) return null
        const sep = raw.indexOf(":")
        if (sep < 0) return null
        const kind = raw.slice(0, sep) as DragKind
        if (kind !== "filter" && kind !== "group" && kind !== "field") return null
        return {kind, id: raw.slice(sep + 1)}
    }

    const onChipDragStart = (event: DragEvent, filterId: string) => {
        if (filter.readOnly?.value) return
        event.stopPropagation() // don't trigger the parent dropzone's group-drag
        draggingEntity.value = {kind: "filter", id: filterId}
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move"
            event.dataTransfer.setData(ENTITY_MIME, `filter:${filterId}`)
            event.dataTransfer.setData("text/plain", filterId)
        }
    }

    const onGroupDragStart = (event: DragEvent, groupId: string) => {
        if (filter.readOnly?.value) return
        event.stopPropagation()
        draggingEntity.value = {kind: "group", id: groupId}
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move"
            event.dataTransfer.setData(ENTITY_MIME, `group:${groupId}`)
            event.dataTransfer.setData("text/plain", groupId)
        }
    }

    const onDragOver = (event: DragEvent, groupId: string) => {
        if (filter.readOnly?.value) return
        const entity = draggingEntity.value
        const isCrossComponentField = entity == null
            && event.dataTransfer?.types.includes(ENTITY_MIME)
        if (entity == null && !isCrossComponentField) return
        // Don't allow dropping a group on itself.
        if (entity?.kind === "group" && entity.id === groupId) return
        event.preventDefault()
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = isCrossComponentField ? "copy" : "move"
        }
        dragOverGroupId.value = groupId
    }

    const onDragLeave = (event: DragEvent, groupId: string) => {
        // Only clear when leaving the dropzone itself, not moving between child elements.
        const current = event.currentTarget as HTMLElement | null
        const related = event.relatedTarget as Node | null
        if (current && (!related || !current.contains(related)) && dragOverGroupId.value === groupId) {
            dragOverGroupId.value = null
        }
    }

    const onDrop = (event: DragEvent, targetGroupId: string) => {
        if (filter.readOnly?.value) return
        event.preventDefault()

        const entity = parseEntity(event.dataTransfer?.getData(ENTITY_MIME)) ?? draggingEntity.value
        if (entity?.kind === "group") {
            filter.wrapGroups(entity.id, targetGroupId)
        } else if (entity?.kind === "filter") {
            filter.moveFilter(entity.id, targetGroupId)
        } else if (entity?.kind === "field") {
            addFieldChipToGroup(entity.id, targetGroupId)
        }

        draggingEntity.value = null
        dragOverGroupId.value = null
    }

    const addFieldChipToGroup = (keyName: string, targetGroupId: string) => {
        const key = filter.configuration.value.keys?.find((k) => k.key === keyName)
        const comparator = key?.comparators?.[0]
        if (!key || !comparator) return
        const newFilter: AppliedFilter = {
            id: `${key.key}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            key: key.key,
            keyLabel: key.label,
            comparator,
            comparatorLabel: COMPARATOR_LABELS[comparator],
            value: [],
            valueLabel: "",
        }
        filter.addFilter(newFilter, targetGroupId)
        isCustomizeFiltersVisible.value = false
        nextTick(() => chipRefs.value[newFilter.id]?.editPopover?.toggleDialog())
    }

    const onFieldDragEnd = () => {
        // Mirror onDragEnd so the highlight clears if the user drops outside any dropzone.
        draggingEntity.value = null
        dragOverGroupId.value = null
    }

    const onDragEnd = () => {
        draggingEntity.value = null
        dragOverGroupId.value = null
    }

    const canReset = computed(() => {
        return (
            !!filter.hasAppliedFilters?.value ||
            !!filter.hasDismissedDefaultVisibleKeys?.value ||
            !!filter.searchQuery?.value
        )
    })

    const setChipRef = (filterId: string, el: any) => el
        ? chipRefs.value[filterId] = el
        : delete chipRefs.value[filterId]

    const handleAddFilter = (newFilter: AppliedFilter) => {
        filter.addFilter(newFilter)
        setTimeout(() => {
            isCustomizeFiltersVisible.value = false
        }, 300)
        nextTick(() => chipRefs.value[newFilter.id]?.editPopover?.toggleDialog())
    }

    const handleReset = () => {
        filter.resetToDefaults()
    }

    const localSearchQuery = ref(filter.searchQuery?.value ?? "")
    watch(() => filter.searchQuery?.value, (v) => {
        if (v !== localSearchQuery.value) localSearchQuery.value = v ?? ""
    })

    const debouncedUpdateSearch = useDebounceFn((value: string) => {
        filter.searchQuery.value = value
    }, 700)
</script>

<style lang="scss" scoped>
.filter-container {
    --ks-box-shadow: 0 1px 2px var(--ks-shadow-surface);

    display: flex;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    gap: .5rem;
    row-gap: 0.5rem;
    flex: 1;
    min-width: 7rem;

    &.filter-grow {
        flex-wrap: nowrap;
        flex-grow: 1;
    }
}

.customize-button {
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

.add-group-btn {
    margin: 0 !important;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    flex-shrink: 0;

    &:hover {
        color: var(--ks-text-primary);
        text-decoration: underline;
    }

    &.filters-hidden {
        display: none;
    }
}
</style>
