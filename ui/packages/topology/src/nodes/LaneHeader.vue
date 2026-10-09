<template>
    <div
        class="lane-header"
        :class="{'lane-header--card': layout === 'card', 'lane-header--pill': isPill}"
        :style="headerStyle"
        :tabindex="loopLane ? 0 : undefined"
        data-test="topology-lane-header"
        @click="onHeaderClick"
        @keydown="onKeydown"
    >
        <div class="lane-main">
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
                <span class="lane-type" :title="typeTooltip">{{ typeLine }}</span>
                <span class="lane-id">{{ idLabel }}</span>
            </div>
            <span v-if="childCount && !chip && !notRun && loopLane?.status !== 'loading'" class="lane-count">{{ $t("topology-graph.child-count", childCount) }}</span>
            <span v-if="notRun" class="lane-not-run" data-test="loop-not-run">{{ $t("topology-graph.loop.not-run") }}</span>
            <component
                :is="chip.failed > 0 ? 'button' : 'span'"
                v-if="chip"
                :type="chip.failed > 0 ? 'button' : undefined"
                class="lane-outcome"
                :class="`lane-outcome--${chip.kind}`"
                data-test="loop-outcome"
                @click.stop="chip.failed > 0 && emit(EVENTS.LOOP_SCOPE_FAILED, {uid: laneUid})"
            >
                <component :is="chipIcon" v-if="chipIcon" class="lane-outcome-icon" />
                <span class="lane-outcome-full" data-test="loop-outcome-full">{{ chipText.full }}</span>
                <span class="lane-outcome-mid" data-test="loop-outcome-mid">{{ chipText.mid }}</span>
                <span class="lane-outcome-min" data-test="loop-outcome-min">{{ chipText.min }}</span>
            </component>
            <span v-if="loopLane && showScope && layout !== 'card'" class="lane-loop-scope">
                <slot name="loopScope" :uid="laneUid" :lane="loopLane" />
            </span>
            <ValidationBadge :issues="validationIssues" />
            <span v-if="stateStyle" class="lane-state" :style="{color: `var(${stateStyle.textVar})`}">
                <component :is="stateStyle.icon" class="lane-state-icon" />
                <span class="lane-state-text">{{ displayedState }}</span>
            </span>
            <span class="lane-actions">
                <slot name="taskActions" :task="taskNode?.task" :actions="actions" :execution="taskExecution" :taskRuns="taskRuns" :taskRun="taskRuns[0]">
                    <NodeMenu :actions="actions" />
                </slot>
            </span>
        </div>
        <div v-if="loopLane && showScope && layout === 'card'" class="lane-loop-row">
            <slot name="loopScope" :uid="laneUid" :lane="loopLane" />
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import {useTaskIcon, SECTIONS, type PluginIconData, type PluginIconMap} from "@kestra-io/design-system"
    import {EVENTS, CLUSTER_TAG_STATUS} from "../utils/constants"
    import * as Utils from "../utils/utils"
    import {getStatusStyle, computeAggregateState} from "../utils/status"
    import {
        loopChip,
        loopChipFromIterations,
        loopOutcome,
        nestedLoopIterationSummary,
        isLoopTaskType,
        type LoopChip,
    } from "../utils/loopOutcome"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import {buildNodeActions, type ActionableTask, type NodeActionsContext} from "../utils/nodeActions"
    import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
    import ValidationBadge from "./ValidationBadge.vue"
    import {
        EXECUTION_INJECTION_KEY,
        LOD_INJECTION_KEY,
        LOOP_LANES_INJECTION_KEY,
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
        EVENTS.LOOP_STEP,
        EVENTS.LOOP_SCOPE_FAILED,
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
    const laneUid = computed(() => props.taskNode?.uid ?? "")
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

    const lod = inject(LOD_INJECTION_KEY, computed(() => "default"))
    const loopLanes = inject(LOOP_LANES_INJECTION_KEY, undefined)

    const isPill = computed(() => lod.value === "pill")

    const loopLane = computed(() =>
        isLoopTaskType(props.taskNode?.task.type) ? loopLanes?.value[laneUid.value] : undefined,
    )

    const outcome = computed(() => {
        const lane = loopLane.value
        return lane?.status === "ready" ? loopOutcome(lane) : undefined
    })

    const chip = computed<LoopChip | undefined>(() => {
        const lane = loopLane.value
        if (!lane) return undefined
        if (outcome.value) return loopChip(outcome.value)
        if (lane.status !== "nested") return undefined
        const summary = nestedLoopIterationSummary(lane, laneUid.value, loopLanes?.value ?? {})
        return summary ? loopChipFromIterations(summary) : undefined
    })

    const showScope = computed(() => Boolean(loopLane.value) && !isPill.value && !notRun.value)

    const notRun = computed(() => loopLane.value?.status === "not-started")

    const displayedState = computed(() =>
        outcome.value ? outcome.value.state : aggregateState.value,
    )

    const stateStyle = computed(() => getStatusStyle(displayedState.value))

    const validationIssues = computed<string[]>(() =>
        validationIssuesByTask?.value?.get(taskId.value) ?? [],
    )

    const {t} = useI18n()

    const chipVariant = computed(() => {
        const current = chip.value
        if (!current) return ""
        return current.kind === "progress" && current.failed > 0 ? "progress-failed" : current.kind
    })

    const chipText = computed(() => {
        const current = chip.value
        if (!current) return {full: "", mid: "", min: ""}
        const named = {failed: current.failed, total: current.total, done: current.done, notStarted: current.notStarted}
        const key = (width: string) => `topology-graph.loop-chip.${chipVariant.value}.${width}`
        return {
            full: t(key("full"), named, current.total),
            mid: t(key("mid"), named, current.total),
            min: t(key("min"), named, current.total),
        }
    })

    const chipIcon = computed(() => {
        const kind = chip.value?.kind
        if (kind === "failed" || kind === "failed-not-started") return CloseCircleOutline
        if (kind === "success") return CheckCircleOutline
        return undefined
    })

    const typeLine = computed(() =>
        props.layout === "card" && chip.value && childCount.value
            ? `${typeLabel.value} · ${t("topology-graph.child-count", childCount.value)}`
            : typeLabel.value,
    )

    function onKeydown(event: KeyboardEvent) {
        if (!loopLane.value || event.altKey || event.ctrlKey || event.metaKey) return
        if ((event.target as HTMLElement | null)?.closest("input, textarea")) return
        if (event.key !== "[" && event.key !== "]") return
        event.preventDefault()
        emit(EVENTS.LOOP_STEP, {uid: laneUid.value, delta: event.key === "]" ? 1 : -1})
    }

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
        flex-direction: column;
        justify-content: center;
        container: lane-header / inline-size;
        max-width: 100%;
        box-sizing: border-box;
        padding: 0 var(--ks-spacing-2);
        border-radius: var(--ks-radius-base) var(--ks-radius-base) 0 0;
        overflow: hidden;
    }

    .lane-header:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: -2px;
    }

    .lane-main {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    // A lane bar spans its whole cluster; the collapsed card is a task node's width instead, and
    // only fits the same row on the tighter gap.
    .lane-header--card .lane-main {
        gap: var(--ks-spacing-1);
    }

    .lane-loop-row {
        display: flex;
        align-items: center;
        min-width: 0;
        margin-top: var(--ks-spacing-1);
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

    .lane-count ~ .lane-actions,
    .lane-outcome ~ .lane-actions,
    .lane-not-run ~ .lane-actions {
        margin-left: 0;
    }

    .lane-not-run {
        flex-shrink: 0;
        margin-left: auto;
        font-size: var(--ks-font-size-2xs);
        opacity: 0.8;
    }

    .lane-outcome {
        display: inline-flex;
        align-items: center;
        flex-shrink: 0;
        gap: var(--ks-spacing-1);
        margin-left: auto;
        padding: 0 var(--ks-spacing-2);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-badge);
        color: var(--ks-text-secondary);
        font: inherit;
        font-size: var(--ks-font-size-2xs);
        font-weight: 600;
        line-height: var(--ks-spacing-5);
    }

    button.lane-outcome {
        cursor: pointer;
    }

    .lane-outcome--failed,
    .lane-outcome--failed-not-started {
        border-color: var(--ks-border-error);
        background: var(--ks-bg-error);
        color: var(--ks-text-error);
    }

    .lane-outcome--success {
        color: var(--ks-text-success);
    }

    .lane-outcome-icon {
        font-size: var(--ks-font-size-sm);
    }

    .lane-outcome-mid,
    .lane-outcome-min {
        display: none;
    }

    .lane-loop-scope {
        display: inline-flex;
        align-items: center;
        flex-shrink: 0;
    }

    @container lane-header (max-width: 36rem) {
        .lane-outcome-full {
            display: none;
        }

        .lane-outcome-mid {
            display: inline;
        }
    }

    @container lane-header (max-width: 24rem) {
        .lane-outcome-mid {
            display: none;
        }

        .lane-outcome-min {
            display: inline;
        }

        .lane-loop-scope {
            display: none;
        }
    }

    .lane-header--pill .lane-outcome-full,
    .lane-header--pill .lane-outcome-mid {
        display: none;
    }

    .lane-header--pill .lane-outcome-min {
        display: inline;
    }
</style>
