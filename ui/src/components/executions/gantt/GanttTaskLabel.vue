<template>
    <GanttTaskIcon :taskType="taskType" />
    <KsTooltip placement="top-start">
        <template #content>
            <code>{{ name }}</code>
            <small v-if="value"><br>{{ value }}</small>
        </template>
        <span class="task-name" data-test="gantt-task-name">
            <code>{{ name }}</code>
            <small v-if="value"> {{ value }}</small>
        </span>
    </KsTooltip>
    <KsTooltip v-if="attempts > 1" placement="right">
        <template #content>
            <span>{{ $t("this_task_has") }} {{ attempts }} {{ $t("attempts").toLowerCase() }}.</span>
        </template>
        <KsIcon class="attempt-warning" size="sm">
            <Warning />
        </KsIcon>
    </KsTooltip>
    <slot />
</template>

<script setup lang="ts">
    import Warning from "vue-material-design-icons/Alert.vue"
    import {KsIcon, KsTooltip} from "@kestra-io/design-system"
    import GanttTaskIcon from "./GanttTaskIcon.vue"

    withDefaults(defineProps<{
        name: string;
        value?: string;
        taskType?: string;
        attempts?: number;
    }>(), {
        value: undefined,
        taskType: undefined,
        attempts: 1,
    })
</script>

<style scoped>
    .task-name {
        overflow-wrap: break-word;

        code {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
        }

        small {
            margin-left: var(--ks-spacing-1);
            font-family: var(--kel-font-family-monospace);
            font-size: var(--ks-font-size-xs);
        }
    }

    .attempt-warning {
        flex-shrink: 0;
        color: var(--ks-text-warning);
    }
</style>
