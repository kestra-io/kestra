<template>
    <div
        v-for="(column, index) in orderedColumns"
        :key="column.prop"
        draggable="true"
        @dragstart="handleDragStart($event, index)"
        @dragover="handleDragOver($event, index)"
        @drop="onDrop($event, index)"
        @dragend="handleDragEnd"
        class="column-item"
        :class="{
            'dragging': draggedIndex === index,
            'drag-over': dragOverIndex === index
        }"
        @click.stop="handleToggle(column)"
    >
        <div class="column-info">
            <Drag class="drag-handle" />
            <div class="column-text">
                <span class="column-label">
                    {{ column.label }}
                </span>
                <small>{{ column.description }}</small>
            </div>
        </div>

        <el-button
            link
            size="default"
            :icon="isVisible(column) ? EyeOutline : EyeOffOutline"
            :class="isVisible(column) ? 'selected' : 'unselected'"
            @click.stop="handleToggle(column)"
        />
    </div>
</template>

<script setup lang="ts">
    import {EyeOutline, EyeOffOutline} from "../filter/utils/icons";
    import {useDragAndDrop} from "../../composables/useDragAndDrop";
    import {useTableColumns, type ColumnConfig} from "../../composables/useTableColumns";
    import Drag from "vue-material-design-icons/Drag.vue";

    const props = defineProps<{
        columns: ColumnConfig[];
        visibleColumns: string[];
        storageKey: string;
    }>();

    const emits = defineEmits<{
        updateColumns: [columns: string[]];
    }>();

    const {
        visibleColumns: localVisibleColumns,
        orderedColumns,
        isVisible,
        toggleColumn,
        reorderColumns
    } = useTableColumns({
        columns: props.columns,
        storageKey: props.storageKey,
        initialVisibleColumns: props.visibleColumns
    });

    const {
        draggedIndex,
        dragOverIndex,
        handleDragStart,
        handleDragOver,
        handleDrop,
        handleDragEnd
    } = useDragAndDrop();

    const handleToggle = (column: ColumnConfig) => {
        toggleColumn(column);
        emits("updateColumns", localVisibleColumns.value);
    };

    const handleReorder = (fromIndex: number, toIndex: number) => {
        reorderColumns(fromIndex, toIndex);
        emits("updateColumns", localVisibleColumns.value);
    };

    const onDrop = (event: DragEvent, targetIndex: number) => {
        handleDrop(event, targetIndex, handleReorder);
    };
</script>

<style lang="scss" scoped>
.column-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.375rem 1rem;
    transition: all 0.2s ease;
    border-bottom: 1px solid var(--ks-border-primary);
    cursor: move;

    &:hover {
        background-color: var(--ks-dropdown-background-hover);
    }

    &:last-child {
        border-bottom: none;
    }

    &.dragging {
        opacity: 0.5;
    }

    &.drag-over {
        background-color: var(--ks-background-secondary);
    }

    .column-info {
        display: flex;
        align-items: center;

        .drag-handle {
            margin-right: 0.5rem;
            color: var(--ks-content-tertiary);
        }

        .column-text {
            display: flex;
            flex-direction: column;
            
            small {
                color: var(--ks-content-tertiary);
                font-size: 0.75rem;
                font-weight: 400;
            }
        }
    }
}
</style>