<template>
    <div
        v-if="isWrapperGroup(unit)"
        class="filter-wrapper-container"
        :class="{
            'filters-hidden': filter.searchInputFullWidth?.value,
            'drag-over': dragOverGroupId === unit.id,
        }"
        @dragover="(e) => $emit('drag-over', e, unit.id)"
        @dragleave="(e) => $emit('drag-leave', e, unit.id)"
        @drop="(e) => $emit('drop', e, unit.id)"
    >
        <template v-for="(childLeaf, childIndex) in unit.children" :key="childLeaf.id">
            <LogicalSeparator
                v-if="childIndex > 0"
                :logical="unit.logical"
                :disabled="filter.readOnly?.value"
                inner
                @change="(op) => filter.setWrapperLogical(unit.id, op)"
            />
            <div
                class="filter-group-dropzone"
                :class="{
                    'drag-over': dragOverGroupId === childLeaf.id,
                    'empty': childLeaf.filters.length === 0,
                }"
                @dragover.stop="(e) => $emit('drag-over', e, childLeaf.id)"
                @dragleave.stop="(e) => $emit('drag-leave', e, childLeaf.id)"
                @drop.stop="(e) => $emit('drop', e, childLeaf.id)"
            >
                <span
                    v-if="!filter.readOnly?.value"
                    class="group-drag-handle"
                    :draggable="true"
                    :title="$t('filter.drag_group')"
                    :aria-label="$t('filter.drag_group')"
                    @dragstart="(e) => $emit('drag-start-group', e, childLeaf.id)"
                    @dragend="$emit('drag-end')"
                ><Drag /></span>
                <div
                    v-for="appliedFilter in childLeaf.filters"
                    :key="appliedFilter.id"
                    class="filter-chip-wrap"
                    :class="{'dragging': isDraggingFilter(appliedFilter.id)}"
                    :draggable="!filter.readOnly?.value"
                    @dragstart="(e) => $emit('drag-start-filter', e, appliedFilter.id)"
                    @dragend="$emit('drag-end')"
                >
                    <FilterChip
                        :ref="(el: any) => setChipRef(appliedFilter.id, el)"
                        :filter="appliedFilter"
                        :filterKey="getFilterKeyConfig(appliedFilter)"
                        :class="{'read-only': filter.readOnly?.value}"
                        class="filter-chip"
                        @remove="filter.removeFilter"
                        @update="filter.updateFilter"
                    />
                </div>
            </div>
        </template>
        <GroupActions
            v-if="!filter.readOnly?.value"
            :canUnwrap="true"
            :canRemove="totalUnits > 1"
            @unwrap="filter.unwrapGroup(unit.id)"
            @remove="filter.removeGroup(unit.id)"
        />
    </div>

    <template v-else>
        <div
            class="filter-group-dropzone"
            :class="{
                'filters-hidden': filter.searchInputFullWidth?.value,
                'drag-over': dragOverGroupId === unit.id,
                'empty': unit.filters.length === 0,
                'collapsed': unit.filters.length === 0 && !draggingEntity && totalUnits <= 1,
            }"
            @dragover="(e) => $emit('drag-over', e, unit.id)"
            @dragleave="(e) => $emit('drag-leave', e, unit.id)"
            @drop="(e) => $emit('drop', e, unit.id)"
        >
            <span
                v-if="!filter.readOnly?.value && totalUnits > 1"
                class="group-drag-handle"
                :draggable="true"
                :title="$t('filter.drag_group')"
                :aria-label="$t('filter.drag_group')"
                @dragstart="(e) => $emit('drag-start-group', e, unit.id)"
                @dragend="$emit('drag-end')"
            ><Drag /></span>
            <div
                v-for="appliedFilter in unit.filters"
                :key="appliedFilter.id"
                class="filter-chip-wrap"
                :class="{'dragging': isDraggingFilter(appliedFilter.id)}"
                :draggable="!filter.readOnly?.value"
                @dragstart="(e) => $emit('drag-start-filter', e, appliedFilter.id)"
                @dragend="$emit('drag-end')"
            >
                <FilterChip
                    :ref="(el: any) => setChipRef(appliedFilter.id, el)"
                    :filter="appliedFilter"
                    :filterKey="getFilterKeyConfig(appliedFilter)"
                    :class="{'read-only': filter.readOnly?.value}"
                    class="filter-chip"
                    @remove="filter.removeFilter"
                    @update="filter.updateFilter"
                />
            </div>
        </div>
        <GroupActions
            v-if="!filter.readOnly?.value && totalUnits > 1"
            :canRemove="true"
            :hidden="filter.searchInputFullWidth?.value"
            @remove="filter.removeGroup(unit.id)"
        />
    </template>
</template>

<script setup lang="ts">
    import {inject} from "vue"

    import FilterChip from "./layout/FilterChip.vue"
    import GroupActions from "./segments/GroupActions.vue"
    import LogicalSeparator from "./segments/LogicalSeparator.vue"

    import {Drag} from "./utils/icons"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"
    import {isWrapperGroup, type AppliedFilter, type FilterGroup} from "./utils/filterTypes"

    const props = defineProps<{
        unit: FilterGroup;
        totalUnits: number;
        dragOverGroupId: string | null;
        draggingEntity: {kind: "filter" | "group" | "field"; id: string} | null;
        setChipRef: (filterId: string, el: any) => void;
    }>()

    defineEmits<{
        "drag-start-filter": [event: DragEvent, filterId: string];
        "drag-start-group": [event: DragEvent, groupId: string];
        "drag-end": [];
        "drag-over": [event: DragEvent, groupId: string];
        "drag-leave": [event: DragEvent, groupId: string];
        drop: [event: DragEvent, groupId: string];
    }>()

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const isDraggingFilter = (id: string) =>
        props.draggingEntity?.kind === "filter" && props.draggingEntity.id === id

    const getFilterKeyConfig = (appliedFilter: AppliedFilter) =>
        filter.configuration.value.keys?.find((key) => key.key === appliedFilter.key) ?? null
</script>

<style lang="scss" scoped>
.filter-group-dropzone {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.25rem;
    border: 1px dashed transparent;
    border-radius: var(--ks-radius-sm);
    transition: border-color 120ms ease-in-out, background-color 120ms ease-in-out;

    &.drag-over {
        border-color: var(--ks-border-default);
        background-color: var(--ks-bg-elevated);
    }

    &.empty {
        min-width: 4rem;
        min-height: 1.75rem;
    }

    // The sole empty group has no chips and nothing can be dragged into it, so its
    // reserved drop-target box is just dead space between the search bar and the
    // "add condition group" link. Collapse it out of layout until a drag begins.
    &.collapsed {
        display: none;
    }

    &.filters-hidden {
        display: none;
    }
}

.filter-wrapper-container {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.25rem 0.5rem;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    background-color: var(--ks-bg-elevated);
    transition: border-color 120ms ease-in-out, box-shadow 120ms ease-in-out;

    &.drag-over {
        box-shadow: 0 0 0 2px var(--ks-border-default);
    }

    &.filters-hidden {
        display: none;
    }
}

.group-drag-handle {
    flex-shrink: 0;
    cursor: grab;
    color: var(--ks-text-dim);
    font-size: var(--ks-font-size-sm);
    padding: 0 0.25rem;
    user-select: none;
    line-height: 1;

    &:hover {
        color: var(--ks-text-secondary);
    }

    &:active {
        cursor: grabbing;
    }
}

.filter-chip-wrap {
    flex-shrink: 0;
    cursor: grab;

    &:active {
        cursor: grabbing;
    }

    &.dragging {
        opacity: 0.5;
    }
}

.filter-chip {
    flex-shrink: 0;
    box-shadow: 0 1px 2px var(--ks-shadow-surface);

    &.read-only {
        pointer-events: none;
        opacity: 0.6;
    }
}
</style>
