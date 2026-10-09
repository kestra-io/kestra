<template>
    <div class="lane-header" :class="{'lane-header--card': layout === 'card'}" :style="headerStyle" data-test="topology-lane-header" @click="onHeaderClick">
        <slot name="lead" />
        <div class="lane-icon">
            <component
                :is="taskIconComponent"
                :cls="taskCls"
                :icons="icons"
                :loadIcon="loadIcon"
                variable="--ks-topology-icon-color"
                onlyIcon
            />
        </div>
        <div class="lane-labels">
            <span class="lane-type" :title="typeTooltip">{{ typeLabel }}</span>
            <span class="lane-id">{{ idLabel }}</span>
        </div>
        <span v-if="childCount" class="lane-count">{{ $t("topology-graph.child-count", childCount) }}</span>
        <ValidationBadge :issues="validationIssues" />
        <span v-if="aggregateStatusStyle" class="lane-state" :style="{color: `var(${aggregateStatusStyle.textVar})`}">
            <component :is="aggregateStatusStyle.icon" class="lane-state-icon" />
            <span class="lane-state-text">{{ aggregateState }}</span>
        </span>
        <span class="lane-actions">
            <slot name="taskActions" :task="taskNode?.task" :actions="actions" :execution="taskExecution" :taskRuns="taskRuns" :taskRun="taskRuns[0]">
                <NodeMenu :actions="actions" />
            </slot>
        </span>
    </div>
</template>
<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import {useTaskIcon, SECTIONS, type PluginIconData, type PluginIconMap} from "@kestra-io/design-system"
    import {EVENTS, CLUSTER_TAG_STATUS} from "../utils/constants"
    import * as Utils from "../utils/utils"
    import {getStatusStyle, computeAggregateState} from "../utils/status"
    import {buildNodeActions, type ActionableTask, type NodeActionsContext} from "../utils/nodeActions"
    import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
    import ValidationBadge from "./ValidationBadge.vue"
    import {
        EXECUTION_INJECTION_KEY,
        SUBFLOWS_EXECUTIONS_INJECTION_KEY,
        VALIDATION_ISSUES_INJECTION_KEY,
    } from "../injectionKeys"

    export interface LaneTaskNode {
        uid: string;
        task: {type: string} & Record<string, unknown>;
    }

    defineOptions({name: "LaneHeader"})

    const props = defineProps<{
        taskNode: LaneTaskNode | null;
        color: string;
        height?: number;
        layout?: "bar" | "card";
        childTaskIds?: string[];
        executionId?: string;
        isReadOnly?: boolean;
        replayEnabled?: boolean;
        icons?: PluginIconMap;
        loadIcon?: (cls: string) => Promise<PluginIconData | undefined>;
    }>()

    const emit = defineEmits([
        EVENTS.EDIT,
        EVENTS.DELETE,
        EVENTS.DUPLICATE,
        EVENTS.SHOW_DESCRIPTION,
        EVENTS.SHOW_CONDITION,
        EVENTS.SHOW_LOGS,
        EVENTS.SHOW_OUTPUTS,
        EVENTS.REPLAY_TASK,
        EVENTS.ADD_ERROR,
    ])

    const headerStyle = computed(() => {
        const status = CLUSTER_TAG_STATUS[props.color] ?? "info"
        return {
            backgroundColor: `color-mix(in srgb, var(--ks-status-${status}) 10%, var(--ks-bg-badge))`,
            color: `var(--ks-status-${status})`,
            ...(props.height ? {height: `${props.height}px`} : {}),
        }
    })

    function onHeaderClick(event: MouseEvent) {
        const target = event.target as HTMLElement | null
        if (target?.closest("button, [role='button']")) return
        const task = props.taskNode?.task
        if (props.isReadOnly || !task) return
        emit(EVENTS.EDIT, {task, section: SECTIONS.TASKS})
    }

    const taskIconComponent = useTaskIcon()
    const taskCls = computed(() => props.taskNode?.task?.type)
    const typeLabel = computed(() => Utils.flowableName(taskCls.value))
    const typeTooltip = computed(() => Utils.shortPluginType(taskCls.value))
    const idLabel = computed(() => Utils.afterLastDot(props.taskNode?.uid ?? ""))
    const taskId = computed(() => idLabel.value ?? "")

    const childCount = computed(() => props.childTaskIds?.length ?? 0)

    const execution = inject(EXECUTION_INJECTION_KEY, undefined)
    const subflowsExecutions = inject(SUBFLOWS_EXECUTIONS_INJECTION_KEY, undefined)
    const validationIssuesByTask = inject(VALIDATION_ISSUES_INJECTION_KEY, undefined)

    const taskExecution = computed(() => {
        const executionId = props.executionId
        if (!executionId) return undefined
        return executionId === execution?.value?.id
            ? execution?.value
            : Object.values(subflowsExecutions?.value ?? {}).find((exec) => exec.id === executionId)
    })

    const taskRunList = computed(() => taskExecution.value?.taskRunList ?? [])

    const taskRuns = computed(() =>
        taskRunList.value.filter((run: {taskId: string}) => run.taskId === taskId.value),
    )

    const aggregateState = computed(() =>
        computeAggregateState(props.childTaskIds ?? [], taskRunList.value),
    )

    const aggregateStatusStyle = computed(() => getStatusStyle(aggregateState.value))

    const validationIssues = computed<string[]>(() =>
        validationIssuesByTask?.value?.get(taskId.value) ?? [],
    )

    const {t} = useI18n()

    const actions = computed<NodeAction[]>(() => {
        const task = props.taskNode?.task as (ActionableTask & {type: string}) | undefined
        const ctx: NodeActionsContext = {
            task,
            taskId: taskId.value,
            isReadOnly: Boolean(props.isReadOnly),
            isFlowable: true,
            expandable: false,
            taskExecution: taskExecution.value,
            taskRuns: taskRuns.value,
            taskRunsWithDynamicChildren: taskRuns.value,
            replayEnabled: Boolean(props.replayEnabled),
            link: undefined,
            actionConfig: undefined,
        }
        return buildNodeActions(ctx, t, {
            onShowDescription: (payload) => emit(EVENTS.SHOW_DESCRIPTION, payload),
            onShowCondition: (payload) => emit(EVENTS.SHOW_CONDITION, payload),
            onShowLogs: (payload) => emit(EVENTS.SHOW_LOGS, payload),
            onShowOutputs: (payload) => emit(EVENTS.SHOW_OUTPUTS, payload),
            onOpenLink: () => {},
            onExpand: () => {},
            onAddError: (payload) => emit(EVENTS.ADD_ERROR, payload),
            onShowCustomAction: () => {},
            onShowDetails: () => {},
            onDuplicate: (payload) => emit(EVENTS.DUPLICATE, payload),
            onDelete: (payload) => emit(EVENTS.DELETE, payload),
            onReplayTask: (payload) => emit(EVENTS.REPLAY_TASK, payload),
        }, {id: taskId.value, type: task?.type ?? ""})
    })
</script>
<style scoped lang="scss">
    .lane-header {
        pointer-events: auto;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        max-width: 100%;
        box-sizing: border-box;
        padding: 0 var(--ks-spacing-2);
        border-radius: var(--ks-radius-base) var(--ks-radius-base) 0 0;
        overflow: hidden;
    }

    // A lane bar spans its whole cluster; the collapsed card is a task node's width instead, and
    // only fits the same row on the tighter gap.
    .lane-header--card {
        gap: var(--ks-spacing-1);
    }

    .lane-icon {
        flex-shrink: 0;
        position: relative;
        width: var(--ks-icon-size-lg);
        height: var(--ks-icon-size-lg);
        overflow: hidden;
    }

    .lane-labels {
        display: flex;
        flex-direction: column;
        justify-content: center;
        min-width: 0;
        line-height: 1.2;
    }

    .lane-type,
    .lane-id {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .lane-type {
        font-size: var(--ks-font-size-2xs);
        font-weight: 500;
        opacity: 0.7;
    }

    .lane-id {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
    }

    .lane-count {
        flex-shrink: 0;
        margin-left: auto;
        font-size: var(--ks-font-size-2xs);
        opacity: 0.8;
    }

    .lane-state {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
        font-size: var(--ks-font-size-2xs);
        font-weight: 600;
    }

    .lane-state-icon {
        font-size: var(--ks-font-size-sm);
    }

    .lane-actions {
        flex-shrink: 0;
        margin-left: auto;
    }

    .lane-count ~ .lane-actions {
        margin-left: 0;
    }
</style>
