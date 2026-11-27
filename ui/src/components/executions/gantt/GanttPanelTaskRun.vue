<template>
    <div class="panel-logs">
        <GanttTaskLogs
            v-if="isRootTask && execution"
            :key="node.key"
            :execution="execution"
            :taskRun="node.taskRun"
            :taskType="node.taskType"
            :flow="executionsStore.flow"
            :levelFilter="tree.levelFilter.value"
        />
        <TaskRunDetails
            v-else-if="!isRootTask"
            :key="node.key"
            :targetExecutionId="node.executionId"
            :taskRunId="node.taskRun.id"
            :targetFlow="tree.flowOf(node)"
            :levelFilter="tree.levelFilter.value"
            hideTaskHeader
            :excludeMetas="LOG_EXCLUDED_METAS"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import type {Log} from "../../../stores/logs"
    import {useExecutionsStore} from "../../../stores/executions"
    import GanttTaskLogs from "../GanttTaskLogs.vue"
    import TaskRunDetails from "../../logs/TaskRunDetails.vue"
    import {useGanttTree, type TaskRunNode} from "./ganttTree"

    const LOG_EXCLUDED_METAS: Array<keyof Log> = ["namespace", "flowId", "taskId", "executionId"]

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const executionsStore = useExecutionsStore()
    const execution = computed(() => executionsStore.execution)
    const isRootTask = computed(() => props.node.executionId === execution.value?.id)
</script>

<style scoped>
    .panel-logs {
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
    }
</style>
