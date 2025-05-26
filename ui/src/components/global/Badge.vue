<template>
    <el-tooltip :content="props.tooltip ?? props.label">
        <el-button type="primary" size="small" class="badge">
            {{ props.label }}
        </el-button>
    </el-tooltip>
</template>

<script setup lang="ts">
    interface Props {
        label: string;
        tooltip?: string;
    }
    const props = defineProps<Props>();
</script>

<style scoped lang="scss">
.badge {
    --el-button-border-color: var(--ks-button-content-primary);

    margin-left: 0.5rem;
    border-color: var(--ks-button-content-primary);
    border-radius: calc(var(--el-border-radius-round) * 2);

    &:hover {
        border-color: var(--ks-button-content-primary);
    }
}
</style>
