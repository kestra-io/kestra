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
                    :color="getSchemeValue(activity.state)"
                >
                    <div class="timeline-row">
                        <span class="timestamp">{{ formatDate(activity.date) }}</span>
                        <span class="state">{{ activity.state }}</span>
                    </div>
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
    padding-left: $spacer;
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

    & :deep(.el-timeline-item__tail) {
        height: inherit;
        top: 40%;
        bottom: 10%;
        left: 4.5px;
        border-left-width: 1px;
    }
}

.timeline-row {
    display: flex;
    flex-wrap: wrap;
    gap: calc($spacer / 2);
    align-items: baseline;

    .timestamp {
        color: var(--ks-content-tertiary);
        white-space: nowrap;
    }

    .state {
        color: var(--ks-content-primary);
        white-space: nowrap;
    }
}
</style>