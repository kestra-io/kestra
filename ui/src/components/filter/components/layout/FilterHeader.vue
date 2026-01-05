<template>
    <div class="filter-header">
        <label class="filter-label">{{ label }}</label>
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

    defineProps<{label: string}>();
    
    const emits = defineEmits<{close: []}>();
</script>

<style lang="scss" scoped>
.filter-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem 1rem 0 1rem;

    .filter-label {
        font-size: 16px;
        margin: 0;
        font-weight: 600;
        color: var(--ks-content-primary);
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