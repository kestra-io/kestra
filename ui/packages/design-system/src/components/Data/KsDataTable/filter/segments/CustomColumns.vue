<template>
    <div class="customize-columns-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize columns") }}</h6>
                <small>{{ $t("filter.drag to reorder columns") }}</small>
            </div>
            <KsButton link :icon="Close" @click="$emit('close')" size="small" class="close-icon" />
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
    import {computed, ref} from "vue"
    import {Close} from "../utils/icons"
    import type {ColumnConfig} from "../composables/useTableColumns"
    import DraggableTableColumns from "../DraggableTableColumns.vue"

    const props = defineProps<{
        storageKey: string;
        columns: ColumnConfig[];
        visibleColumns: string[];
    }>()

    const emits = defineEmits<{
        close: [];
        updateColumns: [columns: string[]];
    }>()

    const currentVisibleColumns = ref<string[]>(props.visibleColumns)

    const totalCount = computed(() => props.columns.length)
    const visibleCount = computed(() => currentVisibleColumns.value.length)

    const handleUpdateColumns = (newColumns: string[]) => {
        currentVisibleColumns.value = newColumns
        emits("updateColumns", newColumns)
    }
</script>

<style lang="scss" scoped>
.customize-columns-panel {
    height: fit-content;
    max-height: 327px;
    display: flex;
    flex-direction: column;
    border-radius: 0.5rem;

    small {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-dim);
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 0.75rem 1rem 0.5rem;
        background-color: var(--ks-bg-active);
        border-bottom: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: var(--ks-font-size-sm);
                font-weight: 700;
            }
        }

        :deep(.close-icon) {
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-base);
            cursor: pointer;
            padding-right: 0;

            &:hover {
                color: var(--ks-text-link);
            }
        }
    }

    .list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-subtle) transparent;
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: 0.5rem 1rem;
        text-align: center;
    }
}

:deep(.column-label) {
    font-size: var(--ks-font-size-sm);
    font-weight: 400;
    line-height: 1.375rem;
}

:deep(.kel-button.selected) {
    color: var(--ks-status-success);

    &:hover {
        color: var(--ks-text-success);
    }
}

:deep(.kel-button.unselected) {
    color: var(--ks-text-dim);

    &:hover {
        color: var(--ks-text-secondary);
    }
}
</style>
