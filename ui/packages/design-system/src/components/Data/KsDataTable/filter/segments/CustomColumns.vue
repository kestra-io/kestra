<template>
    <div class="customize-columns-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize columns") }}</h6>
                <small>{{ $t("filter.drag to reorder columns") }}</small>
            </div>
            <KsButton link :icon="Close" @click="$emit('close')" size="small" class="close-icon" />
        </div>

        <div v-if="showSearch" class="search">
            <KsInput
                v-model="search"
                size="small"
                clearable
                :placeholder="$t('filter.search columns')"
            >
                <template #prefix>
                    <Magnify :size="16" />
                </template>
            </KsInput>
        </div>

        <div class="list">
            <DraggableTableColumns
                v-if="columns.length"
                :columns="columns"
                :visibleColumns="currentVisibleColumns"
                :storageKey="storageKey"
                :search="search"
                @resolved="currentVisibleColumns = $event"
                @update-columns="handleUpdateColumns"
            />
        </div>

        <div class="footer">
            <small data-test="visible-columns-count">{{ visibleCount }} of {{ totalCount }} columns visible</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {Close, Magnify} from "../utils/icons"
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

    // `useTableColumns` captures its column list at setup, so building the list before the page's
    // columns arrive leaves it resolving against nothing; the `v-if` above defers that.
    const currentVisibleColumns = ref<string[]>(props.visibleColumns)
    const search = ref("")

    const SEARCH_THRESHOLD = 12
    const showSearch = computed(() => props.columns.length > SEARCH_THRESHOLD)

    watch(() => props.visibleColumns, (columns) => {
        currentVisibleColumns.value = columns
    })

    const selectableColumns = computed(() => props.columns.filter(c => !c.condition || c.condition()))

    const totalCount = computed(() => selectableColumns.value.length)
    const visibleCount = computed(() => selectableColumns.value.filter(c => currentVisibleColumns.value.includes(c.prop)).length)

    const handleUpdateColumns = (newColumns: string[]) => {
        currentVisibleColumns.value = newColumns
        emits("updateColumns", newColumns)
    }
</script>

<style lang="scss" scoped>
.customize-columns-panel {
    height: fit-content;
    max-height: 60vh;
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

    .search {
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);
        background-color: var(--ks-bg-surface);
        flex-shrink: 0;
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
        background-color: var(--ks-bg-surface);
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
</style>
