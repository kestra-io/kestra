<template>
    <div class="panel-subflow">
        <section v-if="entry.children" class="panel-section" data-test="gantt-panel-subflow">
            <div class="section-head">
                <h3>{{ $t("gantt_subflow.executions", children.length) }}</h3>
                <GanttOpenExecution v-if="child" :execution="child" :label="$t('gantt_subflow.open')" />
            </div>
            <p v-if="children.length === 0" class="panel-empty">{{ $t("gantt_subflow.none") }}</p>
            <template v-else-if="child">
                <KsSkeleton v-if="!childEntry?.execution" animated :rows="2" />
                <p v-else-if="runs.length === 0" class="panel-empty">{{ $t("gantt_iteration_no_tasks") }}</p>
                <div v-else class="mini-list">
                    <GanttPanelListRow
                        v-for="run in runs"
                        :key="run.key"
                        :name="run.taskRun.taskId"
                        :offset="offset(run.taskRun.state.histories)"
                        :histories="run.taskRun.state.histories"
                        :state="run.taskRun.state.current"
                        data-test="gantt-panel-subflow-task-run"
                        @click="tree.select(run, {reveal: true})"
                    >
                        <template #leading>
                            <GanttTaskIcon :taskType="run.taskType" />
                        </template>
                    </GanttPanelListRow>
                </div>
            </template>
            <div v-else class="mini-list">
                <GanttPanelListRow
                    v-for="execution in children"
                    :key="execution.id"
                    :name="execution.flowId"
                    :offset="offset(execution.state.histories)"
                    :histories="execution.state.histories"
                    :state="execution.state.current"
                    data-test="gantt-panel-subflow-execution"
                    @click="router.push(subflowExecutionRoute(execution))"
                />
            </div>
        </section>
        <GanttPanelTaskRun :node="node" />
    </div>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {useRouter} from "vue-router"
    import {KsSkeleton} from "@kestra-io/design-system"
    import type {ExecutionTaskRun} from "../executionTaskRun"
    import GanttTaskIcon from "./GanttTaskIcon.vue"
    import GanttPanelListRow from "./GanttPanelListRow.vue"
    import GanttPanelTaskRun from "./GanttPanelTaskRun.vue"
    import GanttOpenExecution from "./GanttOpenExecution.vue"
    import {useGanttTimeline} from "../ganttTimeline"
    import {subflowExecutionRoute} from "../../../utils/subflowExecutions"
    import {
        createTaskRunNode,
        indexTaskRuns,
        loopAncestors,
        orderTaskRuns,
        subflowChildren,
        taskRunAncestors,
        useGanttTree,
        type TaskRunNode,
    } from "./ganttTree"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const router = useRouter()
    const {offset} = useGanttTimeline()

    const entry = tree.subflowExecutions(props.node.executionId)
    const children = computed(() => subflowChildren(tree, props.node))
    const child = computed(() => children.value.length === 1 ? children.value[0] : undefined)
    const childEntry = computed(() => child.value ? tree.executionEntry(child.value.id) : undefined)

    const runs = computed(() => {
        const owner = child.value
        if (!owner) return []
        const taskRuns = (childEntry.value?.execution?.taskRunList ?? []) as ExecutionTaskRun[]
        const byId = indexTaskRuns(taskRuns)
        const taskType = (taskId: string) => tree.taskTypeIn(owner, taskId)
        return orderTaskRuns(taskRuns, taskType)
            .map(({task}) => createTaskRunNode(owner, task, taskType(task.taskId), taskRunAncestors(byId, task, loopAncestors(props.node))))
    })

    watch(child, (owner) => {
        if (!owner) return
        tree.loadExecution(owner.id)
        tree.loadFlow(owner)
    }, {immediate: true})
</script>

<style scoped>
    .panel-section {
        padding: var(--ks-spacing-3) var(--ks-spacing-4) 0;

        h3 {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-secondary);
        }
    }

    .section-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-2);
    }

    .panel-empty {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .mini-list {
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        overflow: hidden;
    }
</style>
