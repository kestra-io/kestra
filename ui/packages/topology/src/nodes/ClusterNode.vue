<template>
    <div :class="classes">
        <div
            v-if="data.isFlowableLane"
            class="lane-header"
            :style="badgeStyle"
            data-test="topology-lane-header"
            @click="onHeaderClick"
        >
            <span
                v-if="data.collaspsible"
                class="circle-button lane-collapse"
                :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                @click.stop="collapse()"
            >
                <KsTooltip :content="$t('collapse')">
                    <UnfoldLessHorizontal class="button-icon" alt="Collapse lane" />
                </KsTooltip>
            </span>
            <div class="lane-icon">
                <component
                    :is="taskIconComponent"
                    :cls="taskCls"
                    variable="--ks-topology-icon-color"
                    onlyIcon
                />
            </div>
            <span class="lane-type text-color">{{ typeLabel }}</span>
            <span class="lane-id">{{ idLabel }}</span>
            <span v-if="childCount" class="lane-count">{{ $t("topology-graph.child-count", childCount) }}</span>
            <ValidationBadge :issues="validationIssues" />
            <span v-if="aggregateStatusStyle" class="lane-state" :style="{color: `var(${aggregateStatusStyle.textVar})`}">
                <component :is="aggregateStatusStyle.icon" class="lane-state-icon" />
                <span class="lane-state-text">{{ aggregateState }}</span>
            </span>
            <span class="lane-actions">
                <NodeMenu :actions="actions" />
            </span>
        </div>
        <template v-else>
            <span
                class="cluster-badge text-color"
                :style="badgeStyle"
            >{{ clusterName }}</span>
            <div class="top-button-div">
                <span
                    v-if="data.canAddTrigger"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                    data-test="topology-add-trigger"
                    @click="emit(EVENTS.ADD_TRIGGER)"
                >
                    <KsTooltip :content="$t('topology-graph.add-trigger')">
                        <Plus class="button-icon" />
                    </KsTooltip>
                </span>
                <span
                    v-if="data.collaspsible"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                    @click="collapse()"
                >
                    <KsTooltip :content="$t('collapse')">
                        <UnfoldLessHorizontal class="button-icon" alt="Collapse task" />
                    </KsTooltip>
                </span>
            </div>
        </template>
    </div>
</template>
<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsTooltip, useTaskIcon, SECTIONS} from "@kestra-io/design-system"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
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

    defineOptions({inheritAttrs: false})

    interface ClusterData {
        color: string;
        collaspsible?: boolean;
        canAddTrigger?: boolean;
        unused?: boolean;
        isFlowableLane?: boolean;
        isReadOnly?: boolean;
        executionId?: string;
        childTaskIds?: string[];
        taskNode: {
            uid: string;
            task: {type: string} & Record<string, unknown>;
        } | null;
    }

    const props = defineProps<{
        id?: string;
        data: ClusterData;
    }>()

    const emit = defineEmits([
        EVENTS.COLLAPSE,
        EVENTS.ADD_TRIGGER,
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

    const badgeStyle = computed(() => {
        const status = CLUSTER_TAG_STATUS[props.data.color] ?? "info"
        return {
            backgroundColor: `color-mix(in srgb, var(--ks-status-${status}) 10%, var(--ks-bg-badge))`,
            color: `var(--ks-status-${status})`,
        }
    })

    const collapse = () => emit(EVENTS.COLLAPSE, props.id)

    function onHeaderClick(event: MouseEvent) {
        const target = event.target as HTMLElement | null
        if (target?.closest("button, [role='button']")) return
        const task = props.data.taskNode?.task
        if (props.data.isReadOnly || !task) return
        emit(EVENTS.EDIT, {task, section: SECTIONS.TASKS})
    }

    const classes = computed(() => ({"unused-path": props.data.unused}))

    const clusterName = computed(() => {
        const taskNode = props.data.taskNode
        if (taskNode?.task?.type?.toString().endsWith("SubflowGraphTask")) {
            const subflowIdContainer = (taskNode.task.subflowId as Record<string, unknown> | undefined) ?? taskNode.task
            return `${subflowIdContainer.namespace} ${subflowIdContainer.flowId}`
        }
        return Utils.afterLastDot(props.id ?? "")
    })

    const taskIconComponent = useTaskIcon()
    const taskCls = computed(() => props.data.taskNode?.task?.type)
    const typeLabel = computed(() => Utils.shortPluginType(taskCls.value))
    const idLabel = computed(() => Utils.afterLastDot(props.data.taskNode?.uid ?? ""))
    const taskId = computed(() => idLabel.value ?? "")

    const childCount = computed(() => props.data.childTaskIds?.length ?? 0)

    const execution = inject(EXECUTION_INJECTION_KEY, undefined)
    const subflowsExecutions = inject(SUBFLOWS_EXECUTIONS_INJECTION_KEY, undefined)
    const validationIssuesByTask = inject(VALIDATION_ISSUES_INJECTION_KEY, undefined)

    const taskExecution = computed(() => {
        const executionId = props.data.executionId
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
        computeAggregateState(props.data.childTaskIds ?? [], taskRunList.value),
    )

    const aggregateStatusStyle = computed(() => getStatusStyle(aggregateState.value))

    const validationIssues = computed<string[]>(() =>
        validationIssuesByTask?.value?.get(taskId.value) ?? [],
    )

    const {t} = useI18n()

    const actions = computed<NodeAction[]>(() => {
        const task = props.data.taskNode?.task as (ActionableTask & {type: string}) | undefined
        const ctx: NodeActionsContext = {
            task,
            taskId: taskId.value,
            isReadOnly: Boolean(props.data.isReadOnly),
            isFlowable: true,
            expandable: false,
            taskExecution: taskExecution.value,
            taskRuns: taskRuns.value,
            taskRunsWithDynamicChildren: taskRuns.value,
            replayEnabled: false,
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
    .circle-button {
        pointer-events: auto !important;
    }

    .button-icon {
        font-size: var(--ks-font-size-sm);
        transform: rotate(45deg);
    }

    .cluster-badge {
        position: relative;
        display: inline-block;
        max-width: 100%;
        border-radius: var(--ks-radius-base);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .text-color {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
    }

    .top-button-div {
        align-items: center;
    }

    .lane-header {
        pointer-events: auto;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        max-width: 100%;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .lane-collapse {
        flex-shrink: 0;
    }

    .lane-icon {
        flex-shrink: 0;
        position: relative;
        width: var(--ks-icon-size-lg);
        height: var(--ks-icon-size-lg);
        overflow: hidden;
    }

    .lane-type {
        flex-shrink: 0;
        border-radius: var(--ks-radius-sm);
    }

    .lane-id {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
    }

    .lane-count {
        flex-shrink: 0;
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
</style>
