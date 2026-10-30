<template>
    <div class="panel-iteration">
        <section v-if="failure" class="failure-callout" data-test="gantt-first-failure">
            <span class="failure-path">{{ $t("gantt_panel.first_failure") }} · {{ failurePath }}</span>
            <code v-if="failure.kind !== 'loop' && firstFailure.message.value" class="failure-message">{{ firstFailure.message.value }}</code>
            <KsButton size="small" :icon="TextBoxOutline" data-test="gantt-show-failure-logs" @click="tree.revealFirstFailure(failure)">
                {{ $t("gantt_panel.go_to_failure") }}
            </KsButton>
        </section>

        <section class="panel-section">
            <h3>{{ $t("gantt_panel.task_runs") }}</h3>
            <KsSkeleton v-if="!execution && !entry.error" animated :rows="3" />
            <KsAlert v-else-if="!execution" type="error" :closable="false">
                {{ $t("loop_iterations_load_error") }}
                <KsButton size="small" link @click="tree.loadExecution(node.row.id, true)">{{ $t("retry") }}</KsButton>
            </KsAlert>
            <p v-else-if="runs.length === 0" class="panel-empty">{{ $t("gantt_iteration_no_tasks") }}</p>
            <div v-else class="mini-list">
                <GanttPanelListRow
                    v-for="run in runs"
                    :key="run.key"
                    :name="run.taskRun.taskId"
                    :offset="offset(run.taskRun.state.histories)"
                    :histories="run.taskRun.state.histories"
                    :state="run.taskRun.state.current"
                    data-test="gantt-panel-task-run"
                    @click="tree.select(run, {reveal: true})"
                >
                    <template #leading>
                        <GanttTaskIcon :taskType="run.taskType" />
                    </template>
                </GanttPanelListRow>
            </div>
        </section>

        <section class="panel-section">
            <h3>{{ $t("gantt_panel.iteration") }}</h3>
            <dl class="details">
                <dt>{{ $t("gantt_panel.value") }}</dt>
                <dd>{{ node.row.value }}</dd>
                <dt>{{ $t("gantt_panel.index") }}</dt>
                <dd>{{ $t("gantt_panel.index_of", {index: node.row.index + 1, total: totalCount}) }}</dd>
                <dt>{{ $t("gantt_panel.execution") }}</dt>
                <dd><KsId :value="node.row.id" :shrink="false" /></dd>
            </dl>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import TextBoxOutline from "vue-material-design-icons/TextBoxOutline.vue"
    import {KsAlert, KsButton, KsId, KsSkeleton} from "@kestra-io/design-system"
    import type {ExecutionTaskRun} from "../executionTaskRun"
    import GanttTaskIcon from "./GanttTaskIcon.vue"
    import GanttPanelListRow from "./GanttPanelListRow.vue"
    import {useFirstFailure} from "../../../composables/useFirstFailure"
    import {useGanttTimeline} from "../ganttTimeline"
    import {
        createTaskRunNode,
        indexTaskRuns,
        iterationAncestors,
        orderTaskRuns,
        taskRunAncestors,
        useGanttTree,
        type IterationNode,
    } from "./ganttTree"

    const props = defineProps<{
        node: IterationNode;
    }>()

    const tree = useGanttTree()
    const {offset} = useGanttTimeline()

    const entry = tree.executionEntry(props.node.row.id)
    const execution = computed(() => entry.execution)
    const taskRuns = computed(() => (execution.value?.taskRunList ?? []) as ExecutionTaskRun[])
    const ancestors = computed(() => iterationAncestors(props.node))

    const runs = computed(() => {
        const byId = indexTaskRuns(taskRuns.value)
        const owner = {id: props.node.row.id, namespace: props.node.loop.namespace, flowId: props.node.loop.flowId}
        return orderTaskRuns(taskRuns.value, tree.taskType).map(({task}) => createTaskRunNode(
            owner,
            task,
            tree.taskType(task.taskId),
            taskRunAncestors(byId, task, ancestors.value),
        ))
    })

    const firstFailure = useFirstFailure(execution)
    const failure = computed(() => runs.value.find(run => run.taskRun.id === firstFailure.failedTaskRun.value?.id))
    const failurePath = computed(() => failure.value
        ? [...failure.value.ancestors.slice(ancestors.value.length), {label: failure.value.taskRun.taskId}].map(part => part.label).join(" › ")
        : "")
    const totalCount = computed(() => tree.loopHandle(props.node.loop).totalCount.value ?? props.node.row.index + 1)


    tree.loadExecution(props.node.row.id)
</script>

<style scoped>
    .panel-iteration {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
    }

    .failure-callout {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        border: var(--ks-border-width-thin) solid var(--ks-border-error);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-error);
    }

    .failure-path {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);
    }

    .failure-message {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
    }

    .panel-section {
        padding-top: var(--ks-spacing-2);

        h3 {
            margin: 0 0 var(--ks-spacing-2);
            font-size: var(--ks-font-size-xs);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-secondary);
        }
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

    .details {
        display: grid;
        grid-template-columns: 7rem minmax(0, 1fr);
        gap: var(--ks-spacing-1) var(--ks-spacing-3);
        margin: 0;
        font-size: var(--ks-font-size-xs);

        dt {
            color: var(--ks-text-secondary);
        }

        dd {
            margin: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            font-family: var(--ks-font-family-mono);
            color: var(--ks-text-primary);
        }
    }
</style>
