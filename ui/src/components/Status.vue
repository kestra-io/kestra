<template>
    <el-button data-component="FILENAME_PLACEHOLDER" data-test-id="execution-status" class="status" :size="props.size ?? ''" :style="style">
        {{ props.title || FILTERS.cap(props.status) }}
    </el-button>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import * as FILTERS from "../utils/filters";

    const props = defineProps<{
        status: string;
        size?: string;
        title?: string;
        label?: boolean;
    }>();

    const style = computed(() => {
        const statusVarname = props.status?.toLowerCase();
        return {
            color: `var(--ks-content-${statusVarname}) !important`,
            "border-color": `var(--ks-border-${statusVarname}) !important`,
            "background-color": `var(--ks-background-${statusVarname}) !important`,
        };
    });
</script>
<style scoped lang="scss">
    .el-button {
        white-space: nowrap;
        border-radius: var(--el-border-radius-base);
        width: 7rem;
        cursor: default;

        &.no-label {
            padding: 0.5rem;
            line-height: 1;
        }
    }
</style>
