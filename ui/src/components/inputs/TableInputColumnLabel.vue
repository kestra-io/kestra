<template>
    <div :data-test="`table-column-${id}`">
        <span class="table-input-header">
            {{ columnLabel }}
        </span>
        <span class="table-input-type">
            {{ column.type }}
        </span>
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import type {InputMetaData} from "../../stores/executions"


    const column = defineProps<InputMetaData>()
    const columnLabel = computed(() => column.displayName ?? column.id)
</script>

<style lang="scss" scoped>
    .table-input-header {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-bold);
    }

    .table-input-type {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        margin-left: var(--ks-spacing-1);
    }
</style>
