<template>
    <el-timeline>
        <el-timeline-item
            v-for="(entry, hIdx) in props.histories"
            :key="hIdx"
            :timestamp="formatDate(entry.date)"
            :color="State.getStateColor(entry.state)"
        >
            {{ entry.state }}
        </el-timeline-item>
    </el-timeline>
</template>

<script setup lang="ts">
    import type {Histories} from "../../../../../stores/executions";

    import {State} from "@kestra-io/ui-libs";
    import {storageKeys} from "../../../../../utils/constants";

    import moment from "moment";

    const props = defineProps<{ histories: Histories[] }>();

    const F = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? "llll";
    const formatDate = (date: string) => moment(date)?.format(F) ?? date;
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.el-timeline {
    & :deep(.el-timeline-item) {
        padding-bottom: $spacer;
    }

    & :deep(.el-timeline-item__content) {
        font-size: $font-size-sm;
        color: var(--ks-content-primary);
    }

    & :deep(.el-timeline-item__timestamp) {
        margin-top: calc($spacer / 4);
        color: var(--ks-content-tertiary);
    }

    & :deep(.el-timeline-item__tail) {
        height: inherit;
        top: 30%;
        bottom: 10%;
        border-left-width: 1px;
    }
}
</style>
