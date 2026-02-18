<template>
    <div class="filter-header">
        <div class="filter-title">
            <label class="filter-label">{{ label }}</label>
            <small v-if="description" class="filter-description">{{ description }}</small>
        </div>
        <el-button
            link
            size="small"
            :icon="Close"
            @click="emits('close')"
        />
    </div>
</template>

<script setup lang="ts">
    import {Close} from "../../utils/icons";

    defineProps<{
        label: string;
        description?: string;
    }>();
    
    const emits = defineEmits<{close: []}>();
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
        font-size: 16px;
        margin: 0;
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .filter-description {
        margin-top: 0.25rem;
        color: var(--ks-content-secondary);
        font-size: 12px;
        line-height: 1.2;
    }

    :deep(.close-icon) {
        font-size: 16px;
        padding-right: 0;
        cursor: pointer;
        color: var(--ks-content-tertiary);

        &:hover {
            color: var(--ks-content-link);
        }
    }
}
</style>
