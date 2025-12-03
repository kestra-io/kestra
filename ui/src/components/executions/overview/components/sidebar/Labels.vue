<template>
    <el-row v-for="(label, index) in props.labels" :key="index">
        <el-col>
            <span>{{ label.key }}:</span>
            <el-tag :title="label.value">
                {{ label.value }}
            </el-tag>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    const props = defineProps<{
        labels: { key: string; value: string }[];
    }>();
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.el-row {
    margin-bottom: calc($spacer / 1.5) !important;

    .el-col {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: $font-size-sm;

        & :deep(.el-tag) {
            margin-left: calc($spacer / 1.5);
            overflow: hidden;
            background-color: var(--ks-tag-background-active);
            color: var(--ks-tag-content);

            & .el-tag__content {
                display: block;
                min-width: 0;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }
    }
}
</style>
