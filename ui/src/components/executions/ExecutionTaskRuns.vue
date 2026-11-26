<template>
    <div class="execution-task-runs" data-test="iteration-task-runs">
        <GanttMessageRow v-if="!loaded && entry.error" :ancestors="ancestors">
            <KsAlert type="error" :closable="false">
                {{ $t(subflow ? "gantt_subflow.load_error" : "loop_iterations_load_error") }}
                <KsButton size="small" link @click="tree.loadExecution(execution.id, true)">{{ $t("retry") }}</KsButton>
            </KsAlert>
        </GanttMessageRow>
        <GanttMessageRow v-else-if="!loaded" :ancestors="ancestors">
            <KsIcon class="is-loading" size="sm">
                <Loading />
            </KsIcon>
            {{ $t(subflow ? "gantt_subflow.loading" : "gantt_loop_loading") }}
        </GanttMessageRow>
        <template v-else>
            <GanttMessageRow v-if="rows.length === 0" :ancestors="ancestors">
                {{ $t(tree.failedOnly.value ? "gantt_iteration_no_failed_tasks" : subflow ? "gantt_subflow.no_tasks" : "gantt_iteration_no_tasks") }}
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
                    :expandable="node.kind === 'loop' || isSubflowNode(node)"
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
                            :execution="loaded"
                            :flow="tree.flowOf(execution)"
                        />
                    </template>
                </GanttRow>
                <LoopIterationTree
                    v-if="node.kind === 'loop' && tree.isExpanded(node.key)"
                    :key="`${node.executionId}-${node.taskRun.taskId}`"
                    :node="node"
                />
                <SubflowExecutionTree
                    v-else-if="isSubflowNode(node) && tree.isExpanded(node.key)"
                    :key="node.key"
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
    import SubflowExecutionTree from "./SubflowExecutionTree.vue"
    import GanttRow from "./gantt/GanttRow.vue"
    import GanttMessageRow from "./gantt/GanttMessageRow.vue"
    import GanttTaskLabel from "./gantt/GanttTaskLabel.vue"
    import GanttLoopChips from "./gantt/GanttLoopChips.vue"
    import GanttLoopStateBar from "./gantt/GanttLoopStateBar.vue"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {
        createTaskRunNode,
        indexTaskRuns,
        isSubflowNode,
        loopRefOf,
        orderTaskRuns,
        taskRunAncestors,
        useGanttTree,
        type Ancestor,
        type ExecutionRef,
    } from "./gantt/ganttTree"
    import {useGanttTimeline} from "./ganttTimeline"

    const props = defineProps<{
        execution: ExecutionRef;
        ancestors: Ancestor[];
        subflow?: boolean;
    }>()

    const tree = useGanttTree()
    const {offset, barStyle} = useGanttTimeline()

    const entry = tree.executionEntry(props.execution.id)
    const loaded = computed(() => entry.execution)

    const rows = computed(() => {
        const taskRuns = (loaded.value?.taskRunList ?? []) as ExecutionTaskRun[]
        const byId = indexTaskRuns(taskRuns)
        const taskType = (taskId: string) => tree.taskTypeIn(props.execution, taskId)
        return orderTaskRuns(taskRuns, taskType)
            .map(({task}) => createTaskRunNode(props.execution, task, taskType(task.taskId), taskRunAncestors(byId, task, props.ancestors)))
            .filter(node => !tree.failedOnly.value || tree.hasFailure(node))
    })

    onMounted(() => {
        tree.loadExecution(props.execution.id)
        tree.loadFlow(props.execution)
    })
</script>

<style scoped>
    .execution-task-runs {
        display: flex;
        flex-direction: column;
    }
</style>
