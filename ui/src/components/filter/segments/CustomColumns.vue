<template>
    <div class="customize-columns-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize columns") }}</h6>
                <small>{{ $t("filter.drag to reorder columns") }}</small>
            </div>
            <el-button link :icon="Close" @click="$emit('close')" size="small" class="close-icon" />
        </div>

        <div class="list">
            <DraggableTableColumns
                :columns="columns"
                :visibleColumns="currentVisibleColumns"
                :storageKey="storageKey"
                @update-columns="handleUpdateColumns"
            />
        </div>

        <div class="footer">
            <small>{{ visibleCount }} of {{ totalCount }} columns visible</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {Close} from "../utils/icons";
    import type {ColumnConfig} from "../../../composables/useTableColumns";
    import DraggableTableColumns from "../../layout/DraggableTableColumns.vue";

    const props = defineProps<{
        storageKey: string;
        columns: ColumnConfig[];
        visibleColumns: string[];
    }>();

    const emits = defineEmits<{
        close: [];
        updateColumns: [columns: string[]];
    }>();

    const currentVisibleColumns = ref<string[]>(props.visibleColumns);

    const totalCount = computed(() => props.columns.length);
    const visibleCount = computed(() => currentVisibleColumns.value.length);

    const handleUpdateColumns = (newColumns: string[]) => {
        currentVisibleColumns.value = newColumns;
        emits("updateColumns", newColumns);
    };
</script>

<style lang="scss" scoped>
.customize-columns-panel {
    height: fit-content;
    max-height: 327px;
    display: flex;
    flex-direction: column;
    border-radius: 0.5rem;
    
    small {
        font-size: 0.75rem;
        color: var(--ks-content-tertiary);
    }
    
    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 0.75rem 1rem 0.5rem;
        background-color: var(--ks-background-table-header);
        border-bottom: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: 0.875rem;
                font-weight: 700;
            }
        }

        :deep(.close-icon) {
            color: var(--ks-content-tertiary);
            font-size: 1rem;
            cursor: pointer;
            padding-right: 0;

            &:hover {
                color: var(--ks-content-link);
            }
        }
    }

    .list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-secondary) transparent;
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-primary);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: 0.5rem 1rem;
        text-align: center;
    }
}

:deep(.column-label) {
    font-size: 0.875rem;
    font-weight: 400;
    line-height: 1.375rem;
}

:deep(.el-button.selected) {
    color: var(--ks-chart-success);

    &:hover {
        color: var(--ks-content-success);
    }
}

:deep(.el-button.unselected) {
    color: var(--ks-content-tertiary);

    &:hover {
        color: var(--ks-content-secondary);
    }
}
</style>