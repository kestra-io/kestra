<template>
    <el-collapse accordion>
        <el-collapse-item :icon="ChevronDown">
            <template #title>
                <span>{{ $t("state_history") }}</span>
            </template>

            <el-timeline>
                <el-timeline-item
                    v-for="(activity, aIdx) in props.histories"
                    :key="aIdx"
                    :timestamp="formatDate(activity.date)"
                    :color="getSchemeValue(activity.state)"
                >
                    {{ activity.state }}
                </el-timeline-item>
            </el-timeline>
        </el-collapse-item>
    </el-collapse>
</template>

<script setup lang="ts">
    import type {Histories} from "../../../../../stores/executions";

    import {getSchemeValue} from "../../../../../utils/scheme";
    import {storageKeys} from "../../../../../utils/constants";

    import moment from "moment";

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";

    const props = defineProps<{ histories: Histories[] }>();

    const F = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? "llll";
    const formatDate = (date: string) => moment(date)?.format(F === "llll" ? "YYYY-MM-DD HH:mm:ss.SSS" : F) ?? date;
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.el-collapse {
    margin-top: $spacer;

    & :deep(.el-collapse-item__header),
    & :deep(.el-collapse-item__content) {
        padding-bottom: 0;
        background-color: var(--ks-background-table-row);
        font-size: $font-size-sm;
    }

    & :deep(.el-collapse-item__header) {
        padding-top: 0;
    }

    & :deep(.el-collapse-item__header:focus:not(:hover)) {
        color: var(--ks-content-secondary);
    }

    & :deep(.el-collapse-item__arrow.is-active) {
        transform: rotate(180deg);
    }

    & :deep(.el-collapse-item__title) {
        margin-right: calc($spacer / 2);
        text-align: right;
    }
}

.el-timeline {
    padding-left: 200px;
    margin-top: $spacer;

    & :deep(.el-timeline-item) {
        padding-bottom: $spacer;
        position: relative;
    }

    & :deep(.el-timeline-item__timestamp) {
        position: absolute;
        left: -210px;
        width: 190px;
        text-align: right;
        top: -3px;
        margin-top: 0;
        line-height: 1.5;
        font-size: $font-size-sm;
        color: var(--ks-content-tertiary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    & :deep(.el-timeline-item__content) {
        font-size: $font-size-sm;
        color: var(--ks-content-primary);
        line-height: 1.5;
        top: -3px;
        position: relative;
        min-height: auto;

        .timeline-state {
            font-weight: bold;
        }
    }

    & :deep(.el-timeline-item__node) {
        position: absolute;
        z-index: 2;
        top: 2px;
        left: -1px;
        width: 10px;
        height: 10px;
    }

    & :deep(.el-timeline-item__tail) {
        position: absolute;
        display: block !important;
        z-index: 0;
        left: 3px;
        top: 0;
        bottom: 0;
        height: 100%;
        width: 2px;
        background-color: var(--ks-border-color, #5c5c5c);
    }

    & :deep(.el-timeline-item:first-child .el-timeline-item__tail) {
        top: 7px;
        height: calc(100% - 7px);
    }

    & :deep(.el-timeline-item:last-child .el-timeline-item__tail) {
        display: none !important;
    }

    .el-collapse-item {
        background-color: transparent;
    }
}
</style>
