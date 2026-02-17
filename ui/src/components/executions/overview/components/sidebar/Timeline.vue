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

    import moment from "moment";

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";

    const props = defineProps<{ histories: Histories[] }>();

    const formatDate = (date: string) => {
        return moment(date)?.format("YYYY-MM-DD HH:mm:ss.SSS") ?? date;
    };
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
    padding-left: 50%;
    margin-top: $spacer;

    & :deep(.el-timeline-item) {
        padding-bottom: $spacer;

        & * {
            line-height: 1.5;
            font-size: $font-size-sm;
        }
    }

    & :deep(.el-timeline-item__content) {
        color: var(--ks-content-primary);
    }

    & :deep(.el-timeline-item__timestamp) {
        position: absolute;
        top: 0;
        left: -210px;
        width: 190px;
        margin-top: 0;
        text-align: right;
        color: var(--ks-content-tertiary);
    }

    & :deep(.el-timeline-item__tail) {
        height: inherit;
        top: 40%;
        bottom: 10%;
        left: 4.5px;
        border-left-width: 1px;
    }
}
</style>
