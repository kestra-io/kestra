<template>
    <Teleport to="body">
        <Transition name="adv-fade" appear>
            <div v-if="open" class="adv-overlay" @click="open = false">
                <div
                    class="adv-builder"
                    role="dialog"
                    aria-modal="true"
                    :aria-label="$t('filter.advanced_filter')"
                    :style="positionStyle"
                    @click.stop
                >
                    <div class="adv-header">
                        <span class="adv-title">{{ $t("filter.advanced_filter") }}</span>
                        <KsButton link :icon="Close" class="adv-header-close" :aria-label="$t('filter.cancel')" @click="open = false" />
                    </div>

                    <div class="adv-body">
                        <Motion
                            v-for="row in displayRows"
                            :key="row.filter.id"
                            as="div"
                            layout
                            :animate="{
                                opacity: draggedId === row.filter.id ? 0.35 : 1,
                                scale: draggedId === row.filter.id ? 0.98 : 1,
                            }"
                            :transition="ITEM_TRANSITION"
                            class="adv-row"
                            :class="{
                                'new-group': row.isFirstOfGroup && row.groupIndex > 0,
                            }"
                            @dragover.prevent="over(row.filter.id, $event)"
                            @drop.prevent="drop()"
                        >
                            <span
                                class="adv-grip"
                                :draggable="!ctx.readOnly.value"
                                :title="$t('filter.drag to reorder')"
                                @dragstart="onDragStart(row, $event)"
                                @dragend="reset()"
                            ><DotsGrid :size="18" /></span>

                            <button
                                v-if="row.lead"
                                class="adv-conj lead"
                                type="button"
                                disabled
                            >{{ $t("filter.where") }}</button>
                            <button
                                v-else-if="row.isFirstOfGroup"
                                class="adv-conj toggle"
                                type="button"
                                :disabled="ctx.readOnly.value"
                                @click="toggleTopLogical"
                            >{{ ctx.topLogical.value === "OR" ? $t("filter.or") : $t("filter.and") }}</button>
                            <span v-else class="adv-conj">{{ $t("filter.and") }}</span>

                            <ConditionRow
                                :filter="row.filter"
                                :allKeys="ctx.configuration.value.keys ?? []"
                                :readOnly="ctx.readOnly.value"
                                @update="ctx.updateFilter"
                                @remove="ctx.removeFilter"
                            />
                        </Motion>
                    </div>

                    <div class="adv-footer">
                        <KsButton link class="adv-add" :icon="Plus" :disabled="ctx.readOnly.value" @click="addCondition">
                            {{ $t("filter.add_condition") }}
                            <span v-if="rows.length" class="adv-add-op">{{ $t("filter.and") }}</span>
                        </KsButton>
                        <KsButton link class="adv-add" :icon="Plus" :disabled="ctx.readOnly.value" @click="addConditionGroup">
                            {{ $t("filter.add_condition_group") }}
                            <span v-if="rows.length" class="adv-add-op">{{ ctx.topLogical.value === "OR" ? $t("filter.or") : $t("filter.and") }}</span>
                        </KsButton>
                        <button class="adv-clear" type="button" :disabled="ctx.readOnly.value" @click="ctx.clearFilters">
                            {{ $t("filter.reset") }}
                        </button>
                    </div>
                </div>
            </div>
        </Transition>
    </Teleport>
</template>

<script setup lang="ts">
    import {computed, inject, nextTick, onBeforeUnmount, ref, watch} from "vue"
    import {Motion} from "motion-v"
    import DotsGrid from "vue-material-design-icons/DotsGrid.vue"

    import ConditionRow from "./ConditionRow.vue"
    import {useDragAndDrop} from "./composables/useDragAndDrop"
    import {computePlacement} from "./utils/reorderPlacement"
    import {findLeafById} from "./composables/useFilterGroups"
    import {createAppliedFilter, pickStarterField} from "./utils/filterChipFactory"
    import {FILTER_CONTEXT_INJECTION_KEY} from "./utils/filterInjectionKeys"
    import {isWrapperGroup, type AppliedFilter, type FilterGroup} from "./utils/filterTypes"
    import {Close, Plus} from "./utils/icons"

    const ITEM_TRANSITION = {type: "spring", stiffness: 400, damping: 30, mass: 0.6}

    interface Row {
        filter: AppliedFilter;
        groupId: string;
        groupIndex: number;
        isFirstOfGroup: boolean;
        lead: boolean;
    }

    const ctx = inject(FILTER_CONTEXT_INJECTION_KEY)!

    const props = withDefaults(defineProps<{anchor?: string}>(), {
        anchor: ".customize-button",
    })

    const open = defineModel<boolean>()
    const positionStyle = ref<Record<string, string>>({})

    const computePosition = () => {
        const anchor = document.querySelector(props.anchor)
            ?? document.querySelector(".customize-button")
        if (!anchor) return
        const rect = anchor.getBoundingClientRect()
        const panelWidth = document.querySelector<HTMLElement>(".adv-builder")?.offsetWidth ?? 600
        const margin = 8
        const maxLeft = Math.max(margin, window.innerWidth - panelWidth - margin)
        const left = Math.min(Math.max(rect.left, margin), maxLeft)
        positionStyle.value = {
            position: "absolute",
            top: `${rect.bottom + window.scrollY + 6}px`,
            left: `${left + window.scrollX}px`,
        }
    }

    const handleKeydown = (e: KeyboardEvent) => {
        if (e.key === "Escape") {
            e.stopPropagation()
            open.value = false
        }
    }

    watch(open, (visible) => {
        if (visible) {
            nextTick(computePosition)
            window.addEventListener("resize", computePosition)
            window.addEventListener("keydown", handleKeydown)
        } else {
            window.removeEventListener("resize", computePosition)
            window.removeEventListener("keydown", handleKeydown)
        }
    }, {immediate: true})

    onBeforeUnmount(() => {
        window.removeEventListener("resize", computePosition)
        window.removeEventListener("keydown", handleKeydown)
    })

    const isGroupable = (filter: AppliedFilter): boolean =>
        ctx.configuration.value.keys?.find((key) => key.key === filter.key)?.groupable !== false

    const leafEntries = (group: FilterGroup): {filter: AppliedFilter; groupId: string}[] => {
        const entries = isWrapperGroup(group)
            ? group.children.flatMap((child) => child.filters.map((filter) => ({filter, groupId: child.id})))
            : group.filters.map((filter) => ({filter, groupId: group.id}))
        return entries.filter((entry) => isGroupable(entry.filter))
    }

    const rows = computed<Row[]>(() => {
        const flat = ctx.groups.value.flatMap((group, groupIndex) =>
            leafEntries(group).map((entry, index) => ({
                ...entry,
                groupIndex,
                isFirstOfGroup: index === 0,
            })),
        )
        return flat.map((row, index) => ({...row, lead: index === 0}))
    })

    const {draggedId, previewIds, start, over, drop, reset} = useDragAndDrop((orderedIds, dragged) => {
        const groupOf = new Map(rows.value.map(row => [row.filter.id, row.groupId]))
        const placement = computePlacement(orderedIds, dragged, id => groupOf.get(id))
        if (placement) ctx.placeFilter(dragged, placement.targetLeafId, placement.targetIndex)
    })

    const displayRows = computed<Row[]>(() => {
        if (!previewIds.value) return rows.value
        const byId = new Map(rows.value.map(row => [row.filter.id, row]))
        return previewIds.value.map(id => byId.get(id)).filter((row): row is Row => Boolean(row))
    })

    const toggleTopLogical = () =>
        ctx.setTopLogical(ctx.topLogical.value === "OR" ? "AND" : "OR")

    const lastGroupId = computed(() => {
        const groups = ctx.groups.value
        const last = groups[groups.length - 1]
        if (!last) return undefined
        if (isWrapperGroup(last)) return last.children[last.children.length - 1]?.id
        return last.id
    })

    const pickStarter = (groupId?: string) => {
        const targetId = groupId ?? lastGroupId.value
        const leaf = targetId ? findLeafById(ctx.groups.value, targetId) : undefined
        return pickStarterField(ctx.configuration.value.keys ?? [], leaf?.filters ?? [])
    }

    const addStarterCondition = (groupId?: string) => {
        const starter = pickStarter(groupId)
        if (!starter) return
        ctx.addFilter(createAppliedFilter(starter.key.key, starter.key, starter.comparator, [], "", "adv"), groupId)
    }

    const addCondition = () => addStarterCondition(lastGroupId.value)

    const addConditionGroup = () => {
        ctx.addGroup()
        nextTick(() => addStarterCondition(lastGroupId.value))
    }

    const onDragStart = (row: Row, event: DragEvent) => {
        if (ctx.readOnly.value) {
            event.preventDefault()
            return
        }
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move"
            const rowEl = (event.currentTarget as HTMLElement | null)?.closest<HTMLElement>(".adv-row")
            if (rowEl) event.dataTransfer.setDragImage(rowEl, 16, rowEl.offsetHeight / 2)
        }
        start(row.filter.id, rows.value.map(r => r.filter.id))
    }
</script>

<style lang="scss" scoped>
.adv-overlay {
    position: fixed;
    inset: 0;
    z-index: 1500;
}

.adv-builder {
    display: flex;
    flex-direction: column;
    width: 720px;
    max-width: calc(100vw - var(--ks-spacing-6));
    max-height: 70vh;
    overflow: auto;
    background-color: var(--ks-bg-elevated);
    border: 1px solid var(--ks-border-strong);
    border-radius: var(--ks-radius-lg);
    box-shadow: 0 8px 24px var(--ks-shadow-elevated);
    z-index: 1501;
}

.adv-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    position: sticky;
    top: 0;
    background-color: var(--ks-bg-elevated);
    z-index: 1;

    .adv-title {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
    }
}

.adv-header-close {
    margin: 0 !important;
    color: var(--ks-icon-muted);
}

.adv-fade-enter-active,
.adv-fade-leave-active {
    transition: opacity 120ms ease;

    .adv-builder {
        transition: transform 120ms ease, opacity 120ms ease;
    }
}

.adv-fade-enter-from,
.adv-fade-leave-to {
    opacity: 0;

    .adv-builder {
        transform: translateY(-4px);
        opacity: 0;
    }
}

.adv-body {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    background-color: var(--ks-bg-base);
    padding: var(--ks-spacing-2);
}

.adv-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2);
    border-radius: var(--ks-radius-base);
    border: 1px solid transparent;
    transition: background-color 120ms ease;

    &:hover {
        background-color: var(--ks-bg-hover);
    }

    &.new-group {
        border-top: 1px solid var(--ks-border-default);
    }
}

.adv-grip {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.25rem;
    color: var(--ks-text-dim);
    cursor: grab;

    &:active {
        cursor: grabbing;
    }
}

.adv-conj {
    flex-shrink: 0;
    min-width: 2.75rem;
    padding: 0;
    border: none;
    background: none;
    font-family: inherit;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    color: var(--ks-text-primary);
    text-align: left;

    &.toggle {
        cursor: pointer;
        color: var(--ks-text-primary);

        &:hover {
            text-decoration: underline;
        }
    }

    &.lead {
        cursor: default;
    }
}

.adv-footer {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-4);
    background-color: var(--ks-bg-hover);
    border-top: 1px solid var(--ks-border-default);
}

.adv-add {
    margin: 0 !important;
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    color: var(--ks-text-secondary);

    &:hover {
        color: var(--ks-text-primary);
    }
}

.adv-add-op {
    display: inline-flex;
    align-items: center;
    margin-left: var(--ks-spacing-1);
    padding: 0 var(--ks-spacing-1);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-xs);
    background-color: var(--ks-bg-elevated);
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    line-height: 1.5;
    color: var(--ks-text-secondary);
}

.adv-clear {
    margin-left: auto;
    border: none;
    background: none;
    font-family: inherit;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-dim);
    cursor: pointer;

    &:hover {
        color: var(--ks-status-error);
    }
}
</style>
