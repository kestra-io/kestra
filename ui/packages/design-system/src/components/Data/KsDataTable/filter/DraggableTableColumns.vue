<template>
    <Reorder.Group
        as="div"
        axis="y"
        :values="visibleItems"
        @update:values="onReorder"
    >
        <Reorder.Item
            v-for="column in visibleItems"
            :key="column.prop"
            :value="column"
            as="div"
            class="column-item"
            :whileDrag="{scale: 1.02}"
        >
            <div class="column-info">
                <DotsGrid class="drag-handle" :size="18" />
                <div class="column-text">
                    <span class="column-label">
                        {{ column.label }}
                    </span>
                    <small>{{ column.description }}</small>
                </div>
            </div>

            <KsSwitch
                :modelValue="isVisible(column)"
                :aria-label="column.label"
                @click.stop
                @update:modelValue="() => handleToggle(column)"
            />
        </Reorder.Item>
    </Reorder.Group>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue"
    import {Reorder} from "motion-v"
    import DotsGrid from "vue-material-design-icons/DotsGrid.vue"
    import {useTableColumns, type ColumnConfig} from "./composables/useTableColumns"

    const passesCondition = (column: ColumnConfig) => !column.condition || column.condition()

    const props = defineProps<{
        columns: ColumnConfig[];
        visibleColumns: string[];
        storageKey: string;
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

    const orderedItems = ref<ColumnConfig[]>(orderedColumns.value.slice())

    onMounted(() => emits("resolved", localVisibleColumns.value))

    watch(orderedColumns, (cols) => {
        if (cols.map(c => c.prop).join() !== orderedItems.value.map(c => c.prop).join()) {
            orderedItems.value = cols.slice()
        }
    })

    const visibleItems = computed(() => orderedItems.value.filter(passesCondition))

    const onReorder = (items: ColumnConfig[]) => {
        if (items.map(c => c.prop).join() === visibleItems.value.map(c => c.prop).join()) return
        let visibleIndex = 0
        const rebuilt = orderedItems.value.map(column => passesCondition(column) ? items[visibleIndex++] : column)
        orderedItems.value = rebuilt
        setColumnOrder(rebuilt.map(c => c.prop))
        emits("updateColumns", localVisibleColumns.value)
    }

    const handleToggle = (column: ColumnConfig) => {
        toggleColumn(column)
        emits("updateColumns", localVisibleColumns.value)
    }
</script>

<style lang="scss" scoped>
.column-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.375rem 1rem;
    border-bottom: 1px solid var(--ks-border-default);
    cursor: grab;
    user-select: none;
    background: var(--ks-bg-surface);

    &:active {
        cursor: grabbing;
    }

    &:last-child {
        border-bottom: none;
    }

    .column-info {
        display: flex;
        align-items: center;

        .drag-handle {
            margin-right: 0.5rem;
            color: var(--ks-text-dim);
            flex-shrink: 0;
        }

        .column-text {
            display: flex;
            flex-direction: column;

            small {
                color: var(--ks-text-dim);
                font-size: var(--ks-font-size-xs);
                font-weight: 400;
            }
        }
    }
}
</style>
