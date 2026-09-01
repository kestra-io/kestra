<template>
    <template v-if="isSearching">
        <ColumnToggleRow
            v-for="column in filteredColumns"
            :key="column.prop"
            :column="column"
            :checked="isVisible(column)"
            :showDescription="!column.group"
            @toggle="handleToggle(column)"
        />
        <div v-if="!filteredColumns.length" class="empty">
            {{ $t("filter.no columns found") }}
        </div>
    </template>

    <template v-else>
        <Reorder.Group
            as="div"
            axis="y"
            :values="ungroupedItems"
            @update:values="onReorder"
        >
            <Reorder.Item
                v-for="column in ungroupedItems"
                :key="column.prop"
                :value="column"
                as="div"
                :whileDrag="{scale: 1.02}"
            >
                <ColumnToggleRow
                    :column="column"
                    :checked="isVisible(column)"
                    :showHandle="true"
                    @toggle="handleToggle(column)"
                />
            </Reorder.Item>
        </Reorder.Group>

        <div v-for="group in groups" :key="group.name" class="column-group">
            <button type="button" class="group-header" @click="toggleGroup(group.name)">
                <ChevronDown class="chevron" :class="{open: expandedGroups[group.name]}" :size="18" />
                <span class="group-name">{{ group.name }}</span>
                <span class="group-count">{{ group.visibleCount }}/{{ group.columns.length }}</span>
            </button>
            <div v-show="expandedGroups[group.name]">
                <ColumnToggleRow
                    v-for="column in group.columns"
                    :key="column.prop"
                    :column="column"
                    :checked="isVisible(column)"
                    :showDescription="false"
                    @toggle="handleToggle(column)"
                />
            </div>
        </div>
    </template>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue"
    import {Reorder} from "motion-v"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ColumnToggleRow from "./ColumnToggleRow.vue"
    import {useTableColumns, type ColumnConfig} from "./composables/useTableColumns"

    const passesCondition = (column: ColumnConfig) => !column.condition || column.condition()

    const props = defineProps<{
        columns: ColumnConfig[];
        visibleColumns: string[];
        storageKey: string;
        search?: string;
    }>()

    const emits = defineEmits<{
        updateColumns: [columns: string[]];
        resolved: [columns: string[]];
    }>()

    const {
        visibleColumns: localVisibleColumns,
        orderedColumns,
        isVisible,
        toggleColumn,
        setColumnOrder,
    } = useTableColumns({
        columns: props.columns,
        storageKey: props.storageKey,
        initialVisibleColumns: props.visibleColumns,
    })

    const query = computed(() => (props.search ?? "").trim().toLowerCase())
    const isSearching = computed(() => query.value.length > 0)

    const availableColumns = computed(() => orderedColumns.value.filter(passesCondition))

    const filteredColumns = computed(() =>
        availableColumns.value.filter(c => c.label.toLowerCase().includes(query.value)),
    )

    onMounted(() => emits("resolved", localVisibleColumns.value))

    const ungroupedColumns = computed(() => availableColumns.value.filter(c => !c.group))

    const groups = computed(() => {
        const byName = new Map<string, ColumnConfig[]>()
        for (const column of availableColumns.value) {
            if (!column.group) continue
            if (!byName.has(column.group)) byName.set(column.group, [])
            byName.get(column.group)!.push(column)
        }
        return Array.from(byName, ([name, columns]) => ({
            name,
            columns,
            visibleCount: columns.filter(c => isVisible(c)).length,
        }))
    })

    const ungroupedItems = ref<ColumnConfig[]>(ungroupedColumns.value.slice())

    watch(ungroupedColumns, (cols) => {
        if (cols.map(c => c.prop).join() !== ungroupedItems.value.map(c => c.prop).join()) {
            ungroupedItems.value = cols.slice()
        }
    })

    const expandedGroups = ref<Record<string, boolean>>({})
    const toggleGroup = (name: string) => {
        expandedGroups.value[name] = !expandedGroups.value[name]
    }

    const isDraggable = (column: ColumnConfig) => !column.group && passesCondition(column)

    const onReorder = (items: ColumnConfig[]) => {
        if (items.map(c => c.prop).join() === ungroupedItems.value.map(c => c.prop).join()) return
        ungroupedItems.value = items
        // Rebuild in place so grouped and condition-hidden columns keep their slot in the stored order.
        let index = 0
        const rebuilt = orderedColumns.value.map(column => isDraggable(column) ? items[index++] : column)
        setColumnOrder(rebuilt.map(c => c.prop))
        emits("updateColumns", localVisibleColumns.value)
    }

    const handleToggle = (column: ColumnConfig) => {
        toggleColumn(column)
        emits("updateColumns", localVisibleColumns.value)
    }
</script>

<style lang="scss" scoped>
.column-group {
    .group-header {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        width: 100%;
        padding: 0.375rem var(--ks-spacing-4);
        border: none;
        border-bottom: 1px solid var(--ks-border-default);
        background: var(--ks-bg-active);
        color: var(--ks-text-primary);
        cursor: pointer;
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-semibold);

        .chevron {
            color: var(--ks-text-dim);
            transition: transform 0.15s ease;

            &.open {
                transform: rotate(180deg);
            }
        }

        .group-name {
            flex: 1;
            text-align: left;
        }

        .group-count {
            color: var(--ks-text-dim);
            font-weight: var(--ks-font-weight-regular);
            font-size: var(--ks-font-size-xs);
        }
    }
}

.empty {
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    text-align: center;
    color: var(--ks-text-dim);
    font-size: var(--ks-font-size-sm);
}
</style>
