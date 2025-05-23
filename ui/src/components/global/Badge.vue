<template>
    <el-tooltip :content="props.tooltipContent ?? props.content" placement="top">
        <div style="display: inline-block">
            <el-button type="primary" size="small" class="ms-2 badge">
                {{ props.content }}
            </el-button>
        </div>
    </el-tooltip>
</template>

<script setup lang="ts">
    export interface BadgeProps {
        content: string;
        tooltipContent?: string;
    }
    const props = defineProps<BadgeProps>();
</script>

<style scoped lang="scss">
.badge {
    pointer-events: none;
    --el-button-border-color: var(--ks-button-content-primary);
    border-color: var(--ks-button-content-primary);
    border-radius: calc(var(--el-border-radius-round) * 2);
}
</style>
