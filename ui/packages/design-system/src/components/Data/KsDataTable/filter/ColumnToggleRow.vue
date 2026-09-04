<template>
    <div class="column-item" :class="{'is-grouped': !showHandle}">
        <div class="column-info">
            <DotsGrid v-if="showHandle" class="drag-handle" :size="18" />
            <div class="column-text">
                <span class="column-label">{{ column.label }}</span>
                <small v-if="showDescription && column.description">{{ column.description }}</small>
            </div>
        </div>

        <KsSwitch
            :modelValue="checked"
            :aria-label="column.label"
            @click.stop
            @update:modelValue="() => $emit('toggle')"
        />
    </div>
</template>

<script setup lang="ts">
    import DotsGrid from "vue-material-design-icons/DotsGrid.vue"
    import type {ColumnConfig} from "./composables/useTableColumns"

    withDefaults(defineProps<{
        column: ColumnConfig;
        checked: boolean;
        showHandle?: boolean;
        showDescription?: boolean;
    }>(), {
        showHandle: false,
        showDescription: true,
    })

    defineEmits<{
        toggle: [];
    }>()
</script>

<style lang="scss" scoped>
.column-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.375rem var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-default);
    cursor: grab;
    user-select: none;
    background: var(--ks-bg-surface);

    &.is-grouped {
        cursor: default;
        padding-left: 1.75rem;
    }

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
            margin-right: var(--ks-spacing-2);
            color: var(--ks-text-dim);
            flex-shrink: 0;
        }

        .column-text {
            display: flex;
            flex-direction: column;

            small {
                color: var(--ks-text-dim);
                font-size: var(--ks-font-size-xs);
                font-weight: var(--ks-font-weight-regular);
            }
        }
    }
}

:deep(.column-label) {
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-regular);
    line-height: 1.375rem;
}
</style>
