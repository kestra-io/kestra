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
    const formatDate = (date: string) => moment(date)?.format(F) ?? date;
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
