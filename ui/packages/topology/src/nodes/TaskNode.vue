<template>
    <Handle type="source" :position="sourcePosition" :style="sourceHandleStyle" />
    <BasicNode
        :id="id"
        :data="dataWithLink"
        :state="state"
        :class="classes"
        :icons="icons"
        :loadIcon="loadIcon"
        :lod="lod"
        @mouseover="emit(EVENTS.MOUSE_OVER, $event)"
        @mouseleave="emit(EVENTS.MOUSE_LEAVE)"
        :focused="isKeyboardFocused"
        @cardClick="onCardClick"
        @taskDragStart="emit(EVENTS.TASK_DRAG_START, $event)"
        :dragging="props.dragging"
        @taskDragEnd="emit(EVENTS.TASK_DRAG_END)"
    >
        <template v-if="runnerLabel" #runner>
            <span class="runner-badge" :title="runnerLabel" data-test="node-runner-badge">{{ runnerLabel }}</span>
        </template>
        <template #subtitle>
            <span class="type-label">{{ typeLabel }}</span>
        </template>
        <template v-if="hasRetried" #icon-badge>
            <span class="retry-marker" role="img" :title="$t('topology-graph.retried')" :aria-label="$t('topology-graph.retried')" data-test="node-retry-marker">
                <AlertCircle />
            </span>
        </template>
        <template #details>
            <slot name="details" />
        </template>
        <template v-if="execution" #footer>
            <span v-if="loopRunsLabel" class="loop-runs" data-test="loop-task-runs">{{ loopRunsLabel }}</span>
            <Duration v-else compact :histories="histories" :denominator="durationDenominator" />
        </template>
        <template #content>
            <button
                v-if="data.node.task && playgroundEnabled && playgroundReadyToStart"
                type="button"
                class="playground-button"
                @click="emit(EVENTS.RUN_TASK, {task: data.node.task})"
            >
                <KsTooltip style="display: flex;" :content="$t('run task in playground')">
                    <PlayIcon class="button-play-icon" :alt="$t('run task in playground')" />
                </KsTooltip>
            </button>
        </template>
        <template #title-status>
            <ValidationBadge :issues="validationIssues" />
        </template>
        <template v-if="execution" #status-row>
            <button
                v-if="artifactCount > 0"
                type="button"
                class="artifact-badge"
                data-test="node-artifact-badge"
                @click.stop="emit(EVENTS.SHOW_ARTIFACTS, artifactsPayload)"
            >
                <DiamondStone class="artifact-badge__icon" />
                <span>{{ $t("topology-graph.artifacts", {count: artifactCount}, artifactCount) }}</span>
            </button>
            <span
                v-if="statusStyle"
                class="status-chip"
                data-test="node-status-chip"
                :data-state="state"
                :style="{'--chip-color': `var(${statusStyle.chipVar ?? statusStyle.textVar})`}"
            >
                <component :is="statusStyle.icon" class="status-chip__icon" />
                <span v-if="statusStyle.label" class="status-chip__text">{{ $t(statusStyle.label) }}</span>
                <span v-else class="status-chip__text">
                    <Duration :histories="histories" :interval="100" :attemptCount="taskRuns[0]?.attempts?.length" :subject="taskId" />
                </span>
            </span>
        </template>
        <template #title-actions>
            <slot name="taskActions" variant="task" :task="data.node.task" :actions="actions" :execution="taskExecution" :taskRuns="taskRunsWithDynamicChildren" :taskRun="taskRuns[0]">
                <NodeMenu :actions="actions" />
            </slot>
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" :style="targetHandleStyle" />
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import {Handle, Position} from "@vue-flow/core"
    import {
        KsTooltip,
        SECTIONS,
        dayjs,
        type PluginIconData,
        type PluginIconMap,
    } from "@kestra-io/design-system"
    import {type CustomActionConfig, type ShowDetailsConfig, EVENTS} from "../utils/constants"
    import {statusRowHandleStyle} from "../utils/nodeHandles"
    import Duration from "../misc/Duration.vue"
    import * as Utils from "../utils/utils"
    import {getStatusStyle, pickWorstState} from "../utils/status"
    import {buildNodeActions, type NodeActionsContext} from "../utils/nodeActions"
    import type {GraphExecution, GraphTaskRun} from "../utils/vueFlowUtils"
    import BasicNode from "./BasicNode.vue"
    import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
    import ValidationBadge from "./ValidationBadge.vue"
    import {
        EXECUTION_INJECTION_KEY,
        SUBFLOWS_EXECUTIONS_INJECTION_KEY,
        LOD_INJECTION_KEY,
        VALIDATION_ISSUES_INJECTION_KEY,
        FOCUSED_TASK_INJECTION_KEY,
        DRAGGING_NODE_INJECTION_KEY,
        LONGEST_TASK_RUN_DURATION_INJECTION_KEY,
        LOOP_LANES_INJECTION_KEY,
        SCOPED_LONGEST_DURATIONS_INJECTION_KEY,
        ARTIFACT_COUNTS_INJECTION_KEY,
    } from "../injectionKeys"
    import {computeLongestTaskRunDuration} from "../misc/durationBreakdown"
    import {loopTaskContext} from "../utils/loopOutcome"

    import PlayIcon from "vue-material-design-icons/Play.vue"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
    import DiamondStone from "vue-material-design-icons/DiamondStone.vue"

    export interface TaskType {
        id: string;
        type: string;
        default: null;
        description?: string;
        runIf?: unknown;
        errors?: unknown[];
        taskRunner?: {
            type?: string;
        };
        subflowId?: {
            namespace: string;
            flowId: string;
        };
        namespace?: string;
        flowId?: string;
    }

    export interface NodeData {
        node: {
            uid: string;
            type?: string;
            task: TaskType;
        };
        executionId?: string;
        color?: string;
        isReadOnly?: boolean;
        isFlowable?: boolean;
        expandable?: boolean;
        link?: {
            namespace?: string;
            id?: string;
            executionId?: string;
        };
    }

    export interface ExpandData {
        id: string;
        type: string;
    }

    interface TaskRunsPayload {
        id: string | undefined;
        execution: GraphExecution | undefined;
        taskRuns: GraphTaskRun[];
    }

    interface ArtifactsPayload extends TaskRunsPayload {
        task: TaskType;
        actions: NodeAction[];
    }

    const props = withDefaults(defineProps<{
        data: NodeData;
        sourcePosition?: Position;
        targetPosition?: Position;
        id: string;
        icons?: PluginIconMap;
        loadIcon?: (cls: string) => Promise<PluginIconData | undefined>;
        enableSubflowInteraction?: boolean;
        playgroundEnabled: boolean;
        playgroundReadyToStart: boolean;
        replayEnabled?: boolean;
        customActions?: Record<string, CustomActionConfig>;
        showDetails?: Record<string, ShowDetailsConfig>;
        dragging?: boolean;
    }>(), {
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
        enableSubflowInteraction: true,
        icons: undefined,
        loadIcon: undefined,
        replayEnabled: false,
        customActions: () => ({}),
        showDetails: () => ({}),
        dragging: false,
    })

    defineOptions({
        name: "TaskNode",
        inheritAttrs: false,
    })

    const emit = defineEmits<{
        (event: typeof EVENTS.EXPAND, data: ExpandData): void;
        (event: typeof EVENTS.OPEN_LINK, data: {link: NodeData["link"]}): void;
        (event: typeof EVENTS.SHOW_LOGS, data: TaskRunsPayload): void;
        (event: typeof EVENTS.SHOW_OUTPUTS, data: TaskRunsPayload): void;
        (event: typeof EVENTS.SHOW_ARTIFACTS, data: ArtifactsPayload): void;
        (event: typeof EVENTS.REPLAY_TASK, data: TaskRunsPayload): void;
        (event: typeof EVENTS.MOUSE_OVER, data: NodeData["node"]): void;
        (event: typeof EVENTS.MOUSE_LEAVE): void;
        (event: typeof EVENTS.ADD_ERROR, data: {task: TaskType}): void;
        (event: typeof EVENTS.EDIT, data: {task: TaskType; section: string}) :void;
        (event: typeof EVENTS.DELETE, data: {id: string | undefined; section: string}) :void;
        (event: typeof EVENTS.DUPLICATE, data: {id?: string}) :void;
        (event: typeof EVENTS.SHOW_CONDITION, data: {id: string | undefined; task: TaskType; section: string}) :void;
        (event: typeof EVENTS.SHOW_DESCRIPTION, data: {id: string | undefined; description: string}) :void;
        (event: typeof EVENTS.RUN_TASK, data: {task: TaskType}) :void;
        (event: typeof EVENTS.SHOW_CUSTOM_ACTION, data: {task: TaskType; customAction: CustomActionConfig}) :void;
        (event: typeof EVENTS.SHOW_DETAILS, data: {task: TaskType; showDetails: ShowDetailsConfig}) :void;
        (event: typeof EVENTS.TASK_DRAG_START, payload: {nodeId: string; label: string; cls?: string}) :void;
        (event: typeof EVENTS.TASK_DRAG_END) :void;
    }>()

    const execution = inject(EXECUTION_INJECTION_KEY)
    const subflowsExecutions = inject(SUBFLOWS_EXECUTIONS_INJECTION_KEY)
    const lod = inject(LOD_INJECTION_KEY, computed(() => "default"))
    const isDraggingNode = inject(DRAGGING_NODE_INJECTION_KEY, undefined)
    const longestTaskRunDuration = inject(LONGEST_TASK_RUN_DURATION_INJECTION_KEY, computed(() => 0))
    const loopLanes = inject(LOOP_LANES_INJECTION_KEY, undefined)
    const scopedLongestDurations = inject(SCOPED_LONGEST_DURATIONS_INJECTION_KEY, undefined)
    const artifactCounts = inject(ARTIFACT_COUNTS_INJECTION_KEY, undefined)

    function onCardClick() {
        const task = props.data.node.task
        if (props.data.isReadOnly || !task || isDraggingNode?.value) return
        emit(EVENTS.EDIT, {task, section: SECTIONS.TASKS})
    }

    const focusedTaskId = inject(FOCUSED_TASK_INJECTION_KEY, undefined)
    const isKeyboardFocused = computed(() => Boolean(taskId.value) && focusedTaskId?.value === taskId.value)

    const validationIssuesByTask = inject(VALIDATION_ISSUES_INJECTION_KEY, undefined)

    const taskId = computed(() => Utils.afterLastDot(props.id))

    const typeLabel = computed(() => Utils.shortPluginType(props.data.node.task?.type))

    const validationIssues = computed<string[]>(() =>
        validationIssuesByTask?.value?.get(taskId.value ?? "") ?? [],
    )

    const runnerType = computed(() => props.data.node?.task?.taskRunner?.type)

    const runnerLabel = computed(() => {
        const type = runnerType.value
        if (!type) return ""
        const parts = type.split(".")
        const cls = parts.at(-1) ?? ""
        const runnerIdx = parts.indexOf("runner")
        const plugin = runnerIdx > 0 ? parts[runnerIdx - 1] : ""
        const titleCase = (s: string) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : "")
        if (!plugin || cls.toLowerCase().includes(plugin.toLowerCase())) return titleCase(cls)
        return `${plugin.toUpperCase()} ${titleCase(cls)}`
    })

    const taskExecution = computed(() => {
        const executionId = props.data.executionId
        if (executionId) {
            return executionId === execution?.value?.id
                ? execution?.value
                : Object.values(subflowsExecutions?.value || {})
                    .find(exec => exec.id === executionId)
        }
        return undefined
    })

    const taskRunList = computed(() => {
        return taskExecution.value && taskExecution.value.taskRunList
            ? taskExecution.value.taskRunList
            : []
    })

    const taskRuns = computed(() => {
        return taskRunList.value.filter(
            (t) => t.taskId === Utils.afterLastDot(props.data.node.uid),
        )
    })

    // The task's own taskruns plus any dynamically-generated child taskruns (e.g. Ansible
    // plays/tasks) so "show task logs" surfaces their logs too, not just the task's root logs.
    const taskRunsWithDynamicChildren = computed(() => {
        const ids = new Set(taskRuns.value.map((t) => t.id))
        const children = taskRunList.value.filter(
            (t) => t.parentTaskRunId && ids.has(t.parentTaskRunId),
        )
        return [...taskRuns.value, ...children]
    })

    const hasRetried = computed(() => taskRuns.value.some((run) => (run.attempts?.length ?? 0) > 1))

    const isRootExecutionTask = computed(() => !props.data.executionId || props.data.executionId === execution?.value?.id)

    const artifactCount = computed(() =>
        isRootExecutionTask.value ? artifactCounts?.value[taskId.value ?? ""] ?? 0 : 0,
    )

    const rendersStatusRow = computed(() => Boolean(execution?.value) && lod.value !== "pill")
    const sourceHandleStyle = computed(() => statusRowHandleStyle(props.sourcePosition, rendersStatusRow.value))
    const targetHandleStyle = computed(() => statusRowHandleStyle(props.targetPosition, rendersStatusRow.value))

    const state = computed(() => {
        if (!taskRuns.value?.length) {
            return undefined
        }

        if (taskRuns.value.length === 1) {
            return taskRuns.value[0].state.current
        }

        const allStates = taskRuns.value.map((t) => t.state.current)
        return pickWorstState(allStates) ?? allStates[0]
    })

    const durationDenominator = computed(() =>
        taskExecution.value && taskExecution.value !== execution?.value
            ? scopedLongestDurations?.value[taskExecution.value.id ?? ""] ?? computeLongestTaskRunDuration(taskExecution.value.taskRunList ?? [])
            : longestTaskRunDuration.value,
    )

    const loopContext = computed(() =>
        loopLanes ? loopTaskContext(props.data.node.uid, Utils.afterLastDot(props.data.node.uid) ?? "", loopLanes.value) : undefined,
    )

    const loopRunsLabel = computed(() => {
        const summary = loopContext.value?.summary
        if (!summary) return ""
        return summary.failed > 0
            ? t("topology-graph.loop.task-runs-failed", {runs: summary.runs, failed: summary.failed}, summary.runs)
            : t("topology-graph.loop.task-runs", {runs: summary.runs}, summary.runs)
    })

    const artifactsPayload = computed<ArtifactsPayload>(() => ({
        id: taskId.value,
        task: props.data.node.task,
        execution: taskExecution.value,
        taskRuns: taskRuns.value,
        actions: actions.value,
    }))

    const classes = computed(() => ({
        "execution-no-taskrun":
            Boolean(taskExecution.value && taskRuns.value && taskRuns.value.length === 0),
        "node-core--error": validationIssues.value.length > 0,
    }))

    const statusStyle = computed(() => getStatusStyle(state.value))

    const histories = computed(() => {
        const run = taskRuns.value?.[0]
        if (!run?.state?.histories?.length) return []
        return run.state.histories.map((h: {date: string; state: string}) => ({
            date: dayjs(h.date),
            state: h.state,
        }))
    })

    const expandData = computed<ExpandData>(() => ({
        id: props.id,
        type: String(props.data.node.task.type),
    }))

    const dataWithLink = computed(() => {
        if (props.data.node.type?.endsWith("SubflowGraphTask") && props.enableSubflowInteraction) {
            const subflowIdContainer = props.data.node.task.subflowId ?? props.data.node.task
            return {
                ...props.data,
                link: {
                    namespace: subflowIdContainer.namespace,
                    id: subflowIdContainer.flowId,
                    executionId: taskRuns.value.find((taskRun) => taskRun.outputs?.executionId)?.outputs?.executionId,
                },
            }
        }
        return props.data
    })

    const actionConfig = computed(() => {
        const taskType = props.data.node.task?.type as string | undefined
        if (!taskType) return undefined
        const customAction = props.customActions?.[taskType] ?? (runnerType.value ? props.customActions?.[runnerType.value] : undefined)
        if (customAction) return {config: customAction, eventName: "showCustomAction"} as const
        const showDetail = props.showDetails?.[taskType]
        if (showDetail) return {config: showDetail, eventName: "showDetails"} as const
        return undefined
    })

    const {t} = useI18n()

    const actions = computed<NodeAction[]>(() => {
        const ctx: NodeActionsContext = {
            task: props.data.node.task,
            taskId: taskId.value ?? "",
            isReadOnly: Boolean(props.data.isReadOnly),
            isFlowable: Boolean(props.data.isFlowable),
            expandable: Boolean(props.data.expandable),
            taskExecution: taskExecution.value,
            taskRuns: taskRuns.value,
            taskRunsWithDynamicChildren: taskRunsWithDynamicChildren.value,
            replayEnabled: props.replayEnabled,
            link: dataWithLink.value.link,
            actionConfig: actionConfig.value,
        }
        return buildNodeActions(ctx, t, {
            onShowDescription: (payload) => emit(EVENTS.SHOW_DESCRIPTION, payload),
            onShowCondition: (payload) => emit(EVENTS.SHOW_CONDITION, {...payload, task: props.data.node.task}),
            onShowLogs: (payload) => emit(EVENTS.SHOW_LOGS, {...payload, execution: taskExecution.value, taskRuns: taskRunsWithDynamicChildren.value}),
            onShowOutputs: (payload) => emit(EVENTS.SHOW_OUTPUTS, {...payload, execution: taskExecution.value, taskRuns: taskRuns.value}),
            onOpenLink: (payload) => emit(EVENTS.OPEN_LINK, payload),
            onExpand: (payload) => emit(EVENTS.EXPAND, payload),
            onAddError: () => emit(EVENTS.ADD_ERROR, {task: props.data.node.task}),
            onShowCustomAction: (payload) => emit(EVENTS.SHOW_CUSTOM_ACTION, {...payload, task: props.data.node.task}),
            onShowDetails: (payload) => emit(EVENTS.SHOW_DETAILS, {...payload, task: props.data.node.task}),
            onDuplicate: (payload) => emit(EVENTS.DUPLICATE, payload),
            onDelete: (payload) => emit(EVENTS.DELETE, payload),
            onReplayTask: (payload) => emit(EVENTS.REPLAY_TASK, {...payload, execution: taskExecution.value, taskRuns: taskRuns.value}),
        }, expandData.value)
    })

</script>

<style scoped>
.playground-button {
    position: absolute;
    bottom: 0;
    right: 0;
    z-index: 1;
    border: none;
    background-color: var(--ks-bg-surface);
    border-radius: 3px;
    height: 1rem;
    width: 1rem;
    padding: .1rem;
    margin: 6px;
    font-size: var(--ks-font-size-sm);
}

button.playground-button {
    color: var(--ks-white);
    background-color: var(--ks-toggle-playground);
}

.status-chip,
.artifact-badge {
    background-color: var(--ks-bg-surface);
    background-image: linear-gradient(var(--chip-tint), var(--chip-tint));
}

.status-chip {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    box-sizing: border-box;
    padding: 0 var(--ks-spacing-2);
    border-radius: var(--ks-radius-sm);
    --chip-tint: color-mix(in srgb, var(--chip-color) 10%, transparent);
    color: var(--chip-color);
    line-height: 1;
}

.status-chip__text {
    font-size: var(--ks-font-size-2xs);
    font-weight: var(--ks-font-weight-semibold);
    white-space: nowrap;
}

.artifact-badge {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    box-sizing: border-box;
    padding: 0 var(--ks-spacing-2);
    border: none;
    border-radius: var(--ks-radius-sm);
    --chip-tint: color-mix(in srgb, var(--ks-status-running) 10%, transparent);
    color: var(--ks-status-running);
    font-size: var(--ks-font-size-2xs);
    font-weight: var(--ks-font-weight-semibold);
    line-height: 1;
    white-space: nowrap;
    cursor: pointer;
}

.artifact-badge__icon {
    font-size: var(--ks-font-size-lg);
}

.retry-marker {
    position: absolute;
    right: calc(var(--ks-spacing-1) * -1);
    bottom: calc(var(--ks-spacing-1) * -1);
    display: flex;
    border-radius: 50%;
    background-color: var(--ks-bg-surface);
    color: var(--ks-status-warning);
    font-size: var(--ks-font-size-lg);
}

.loop-runs {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--ks-font-size-2xs);
    color: var(--ks-text-secondary);
}

.type-label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.runner-badge {
    flex-shrink: 0;
    padding: 0 var(--ks-spacing-1);
    border-radius: var(--ks-radius-xs);
    background-color: var(--ks-bg-tag);
    color: var(--ks-text-info);
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

</style>
