<template>
    <div class="filter-header">
        <div class="filter-title">
            <label class="filter-label">{{ label }}</label>
            <small v-if="description" class="filter-description">{{ description }}</small>
        </div>
        <KsButton
            link
            size="small"
            :icon="Close"
            @click="emits('close')"
        />
    </div>
</template>

<script setup lang="ts">
    import {Close} from "../utils/icons"

    defineProps<{
        label: string;
        description?: string;
    }>()

    const emits = defineEmits<{close: []}>()
</script>

<style lang="scss" scoped>
.filter-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: 1rem 1rem 0 1rem;
    gap: 0.5rem;

    .filter-title {
        min-width: 0;
        display: flex;
        flex-direction: column;
    }

    .filter-label {
        font-size: var(--ks-font-size-base);
        margin: 0;
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .filter-description {
        margin-top: 0.25rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        line-height: 1.2;
    }

    :deep(.close-icon) {
        font-size: var(--ks-font-size-base);
        padding-right: 0;
        cursor: pointer;
        color: var(--ks-text-dim);

        &:hover {
            color: var(--ks-text-link);
        }
    }
}
</style>
