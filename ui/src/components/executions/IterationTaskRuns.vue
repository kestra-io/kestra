<template>
    <div class="iteration-task-runs" data-test="iteration-task-runs">
        <GanttMessageRow v-if="!execution && entry.error" :ancestors="ancestors">
            <KsAlert type="error" :closable="false">
                {{ $t("loop_iterations_load_error") }}
                <KsButton size="small" link @click="tree.loadExecution(iteration.row.id, true)">{{ $t("retry") }}</KsButton>
            </KsAlert>
        </GanttMessageRow>
        <GanttMessageRow v-else-if="!execution" :ancestors="ancestors">
            <KsIcon class="is-loading" size="sm">
                <Loading />
            </KsIcon>
            {{ $t("gantt_loop_loading") }}
        </GanttMessageRow>
        <template v-else>
            <GanttMessageRow v-if="rows.length === 0" :ancestors="ancestors">
                {{ $t(tree.failedOnly.value ? "gantt_iteration_no_failed_tasks" : "gantt_iteration_no_tasks") }}
            </GanttMessageRow>
            <template v-for="node in rows" :key="node.key">
                <GanttRow
                    :nodeKey="node.key"
                    :label="node.taskRun.taskId"
                    :ancestors="node.ancestors"
                    :offset="offset(node.taskRun.state.histories)"
                    :barStyle="barStyle(node.taskRun.state.histories, node.taskRun.state.current)"
                    :state="node.taskRun.state.current"
                    :selected="tree.selected.value?.key === node.key"
                    :expandable="node.kind === 'loop'"
                    :expanded="tree.isExpanded(node.key)"
                    data-test="iteration-task-run"
                    @select="tree.select(node)"
                    @toggle="tree.toggleExpanded(node.key)"
                >
                    <template #label>
                        <GanttTaskLabel
                            :name="node.taskRun.taskId"
                            :value="node.taskRun.value"
                            :taskType="node.taskType"
                            :attempts="node.taskRun.attempts?.length ?? 1"
                        >
                            <GanttLoopChips
                                v-if="node.kind === 'loop'"
                                :loop="loopRefOf(node)"
                                :state="node.taskRun.state.current"
                                :nodeKey="node.key"
                            />
                        </GanttTaskLabel>
                    </template>
                    <template v-if="node.kind === 'loop'" #label-footer>
                        <GanttLoopStateBar :node="node" />
                    </template>
                    <template #duration>
                        <Duration :histories="node.taskRun.state.histories" :attemptCount="node.taskRun.attempts?.length ?? 1" :subject="node.taskRun.taskId" />
                    </template>
                    <template #actions>
                        <TaskRunActions
                            :taskRun="node.taskRun"
                            :taskType="node.taskType"
                            :execution="execution"
                            :flow="executionsStore.flow"
                        />
                    </template>
                </GanttRow>
                <LoopIterationTree
                    v-if="node.kind === 'loop' && tree.isExpanded(node.key)"
                    :key="`${node.executionId}-${node.taskRun.taskId}`"
                    :node="node"
                />
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted} from "vue"
    import Loading from "vue-material-design-icons/Loading.vue"
    import {Duration} from "@kestra-io/topology"
    import {KsAlert, KsButton, KsIcon} from "@kestra-io/design-system"
    import TaskRunActions from "./TaskRunActions.vue"
    import LoopIterationTree from "./LoopIterationTree.vue"
    import GanttRow from "./gantt/GanttRow.vue"
    import GanttMessageRow from "./gantt/GanttMessageRow.vue"
    import GanttTaskLabel from "./gantt/GanttTaskLabel.vue"
    import GanttLoopChips from "./gantt/GanttLoopChips.vue"
    import GanttLoopStateBar from "./gantt/GanttLoopStateBar.vue"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useExecutionsStore} from "../../stores/executions"
    import {
        createTaskRunNode,
        indexTaskRuns,
        iterationAncestors,
        loopRefOf,
        orderTaskRuns,
        taskRunAncestors,
        useGanttTree,
        type IterationNode,
    } from "./gantt/ganttTree"
    import {useGanttTimeline} from "./ganttTimeline"

    const props = defineProps<{
        iteration: IterationNode;
    }>()

    const executionsStore = useExecutionsStore()
    const tree = useGanttTree()
    const {offset, barStyle} = useGanttTimeline()

    const entry = tree.executionEntry(props.iteration.row.id)
    const execution = computed(() => entry.execution)
    const ancestors = computed(() => iterationAncestors(props.iteration))

    const rows = computed(() => {
        const taskRuns = (execution.value?.taskRunList ?? []) as ExecutionTaskRun[]
        const base = ancestors.value
        const byId = indexTaskRuns(taskRuns)
        return orderTaskRuns(taskRuns, tree.taskType)
            .map(({task}) => task)
            .map(task => createTaskRunNode(
                {id: props.iteration.row.id, namespace: props.iteration.loop.namespace, flowId: props.iteration.loop.flowId},
                task,
                tree.taskType(task.taskId),
                taskRunAncestors(byId, task, base),
            ))
            .filter(node => !tree.failedOnly.value || tree.hasFailure(node))
    })

    onMounted(() => tree.loadExecution(props.iteration.row.id))
</script>

<style scoped>
    .iteration-task-runs {
        display: flex;
        flex-direction: column;
    }
</style>
