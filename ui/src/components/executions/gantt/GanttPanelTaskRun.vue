<template>
    <div class="panel-logs">
        <div v-if="subflowExecutions.length" class="subflow-executions">
            <KsButton
                v-for="child in subflowExecutions"
                :key="child.id"
                :tag="RouterLink"
                :to="subflowExecutionRoute(child)"
                size="small"
                :icon="AxisYArrow"
                data-test="gantt-subflow-execution"
            >
                {{ $t("open_subflow_execution", {flow: child.flowId}) }}
            </KsButton>
        </div>
        <GanttTaskLogs
            v-if="!isIterationTask && execution"
            :key="node.key"
            :execution="execution"
            :taskRun="node.taskRun"
            :taskType="node.taskType"
            :flow="executionsStore.flow"
            :levelFilter="tree.levelFilter.value"
        />
        <TaskRunDetails
            v-else-if="isIterationTask"
            :key="node.key"
            :targetExecutionId="node.executionId"
            :taskRunId="node.taskRun.id"
            :targetFlow="executionsStore.flow"
            :levelFilter="tree.levelFilter.value"
            hideTaskHeader
            :excludeMetas="LOG_EXCLUDED_METAS"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink} from "vue-router"
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue"
    import {KsButton} from "@kestra-io/design-system"
    import type {Log} from "../../../stores/logs"
    import {useExecutionsStore} from "../../../stores/executions"
    import GanttTaskLogs from "../GanttTaskLogs.vue"
    import TaskRunDetails from "../../logs/TaskRunDetails.vue"
    import {owningIteration, useGanttTree, type TaskRunNode} from "./ganttTree"
    import {isSubflowTaskType} from "../../../services/subflowReferences"
    import {subflowExecutionRoute, useSubflowExecutions} from "../../../composables/useSubflowExecutions"

    const LOG_EXCLUDED_METAS: Array<keyof Log> = ["namespace", "flowId", "taskId", "executionId"]

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const executionsStore = useExecutionsStore()
    const execution = computed(() => executionsStore.execution)
    const isIterationTask = computed(() => owningIteration(props.node) !== undefined)

    const {subflowExecutionsOf} = useSubflowExecutions(() => isSubflowTaskType(props.node.taskType)
        ? [{id: props.node.executionId, state: props.node.taskRun.state}]
        : [])
    const subflowExecutions = computed(() => subflowExecutionsOf(props.node.executionId)
        .filter(child => child.taskRunId === props.node.taskRun.id))
</script>

<style scoped>
    .panel-logs {
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
    }

    .subflow-executions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-3);
    }
</style>
