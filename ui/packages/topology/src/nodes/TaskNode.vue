<template>
    <Handle type="source" :position="sourcePosition" />
    <BasicNode
        :id="id"
        :data="dataWithLink"
        :state="state"
        :class="classes"
        :icons="icons"
        :iconComponent="iconComponent"
        @mouseover="emit(EVENTS.MOUSE_OVER, $event)"
        @mouseleave="emit(EVENTS.MOUSE_LEAVE)"
    >
        <template #badge>
            <span v-if="runnerLabel" class="runner-badge" :title="runnerLabel">{{ runnerLabel }}</span>
        </template>
        <template #details>
            <Transition name="details-slide">
                <div v-if="globalShowExtraDetails" class="details-wrapper">
                    <slot name="details" />
                </div>
            </Transition>
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
            <span v-if="statusStyle" class="status-tag" :style="{color: `var(${statusStyle.textVar})`}">
                <component :is="statusStyle.icon" class="status-tag__icon" />
                <span v-if="statusStyle.label" class="status-tag__text">{{ $t(statusStyle.label) }}</span>
                <span v-else class="status-tag__text">
                    <Duration :histories="histories" :interval="100" />
                </span>
            </span>
        </template>
        <template #title-actions>
            <NodeMenu :actions="actions" />
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import moment from "moment"
    import {useI18n} from "vue-i18n"
    import {Handle, Position} from "@vue-flow/core"
    import {State, KsTooltip, SECTIONS} from "@kestra-io/design-system"
    import {type CustomActionConfig, type ShowDetailsConfig, EVENTS} from "../utils/constants"
    import Duration from "../misc/Duration.vue"
    import * as Utils from "../utils/utils"
    import {getStatusStyle} from "../utils/status"
    import BasicNode from "./BasicNode.vue"
    import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
    import {
        EXECUTION_INJECTION_KEY,
        SUBFLOWS_EXECUTIONS_INJECTION_KEY,
        SHOW_EXTRA_DETAILS_INJECTION_KEY,
    } from "../injectionKeys"

    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
    import SendLock from "vue-material-design-icons/SendLock.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue"
    import PlayIcon from "vue-material-design-icons/Play.vue"
    

    interface TaskType {
        id: string;
        type: string;
        default: null;
        description?: string;
        runIf?: unknown;
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

    interface NodeData {
        node: {
            uid: string;
            type?: string;
            task: TaskType;
            taskRun: TaskRun
        };
        executionId?: string;
        color?: string;
        isReadOnly?: boolean;
        isFlowable?: boolean;
        expandable?: boolean;
        link?: {
            namespace: string;
            id: string;
            executionId?: string;
        };
    }

    interface TaskRun {
        id: string
        taskId: string;
        state: {
            current: [string, string];
            duration?: string;
            histories?: {date: string; state: string}[];
        };
        outputs?: {
            executionId?: string;
        };
    }

    interface ExpandData {
        id: string;
        type: string;
    }

    const props = withDefaults(defineProps<{
        data: NodeData;
        sourcePosition?: Position;
        targetPosition?: Position;
        id: string;
        icons?: Record<string, unknown>;
        iconComponent?: object;
        enableSubflowInteraction?: boolean;
        playgroundEnabled: boolean;
        playgroundReadyToStart: boolean;
        customActions?: Record<string, CustomActionConfig>;
        showDetails?: Record<string, ShowDetailsConfig>;
    }>(), {
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
        enableSubflowInteraction: true,
        icons: undefined,
        iconComponent: undefined,
        customActions: () => ({}),
        showDetails: () => ({}),
    })

    defineOptions({
        name: "TaskNode",
        inheritAttrs: false,
    })

    const emit = defineEmits<{
        (event: typeof EVENTS.EXPAND, data: any): void;
        (event: typeof EVENTS.OPEN_LINK, data: any): void;
        (event: typeof EVENTS.SHOW_LOGS, data: any): void;
        (event: typeof EVENTS.MOUSE_OVER, data: any): void;
        (event: typeof EVENTS.MOUSE_LEAVE): void;
        (event: typeof EVENTS.ADD_ERROR, data: { task: any }): void;
        (event: typeof EVENTS.EDIT, data: any) :void;
        (event: typeof EVENTS.DELETE, data: any) :void;
        (event: typeof EVENTS.ADD_TASK, data: any) :void;
        (event: typeof EVENTS.SHOW_CONDITION, data: any) :void;
        (event: typeof EVENTS.SHOW_DESCRIPTION, data: any) :void;
        (event: typeof EVENTS.RUN_TASK, data: { task: any }) :void;
        (event: typeof EVENTS.SHOW_CUSTOM_ACTION, data: { task: any; customAction: CustomActionConfig }) :void;
        (event: typeof EVENTS.SHOW_DETAILS, data: { task: any; showDetails: ShowDetailsConfig }) :void;
    }>()

    const execution = inject(EXECUTION_INJECTION_KEY)
    const subflowsExecutions = inject(SUBFLOWS_EXECUTIONS_INJECTION_KEY)
    const globalShowExtraDetails = inject(SHOW_EXTRA_DETAILS_INJECTION_KEY)

    const taskId = computed(() => Utils.afterLastDot(props.id))

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
                    .find((exec: any) => exec.id === executionId)
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
            (t: TaskRun) => t.taskId === Utils.afterLastDot(props.data.node.uid),
        )
    })

    const state = computed(() => {
        if (!taskRuns.value?.length) {
            return null
        }

        if (taskRuns.value.length === 1) {
            return taskRuns.value[0].state.current
        }

        const allStates = taskRuns.value.map((t: TaskRun) => t.state.current)

        const SORT_STATUS: string[] = [
            State.FAILED,
            State.KILLED,
            State.WARNING,
            State.SKIPPED,
            State.KILLING,
            State.RUNNING,
            State.SUCCESS,
            State.RESTARTED,
            State.CREATED,
        ]

        const result = allStates
            .map((item: [string, string]) => {
                const n = SORT_STATUS.indexOf(item[1])
                return [n, item] as [number, [string, string]]
            })
            .sort()
            .map((j: [number, [string, string]]) => j[1])

        return result[0]
    })

    const classes = computed(() => ({
        "execution-no-taskrun":
            Boolean(taskExecution.value && taskRuns.value && taskRuns.value.length === 0),
    }))

    const statusStyle = computed(() => getStatusStyle(state.value))

    const histories = computed(() => {
        const run = taskRuns.value?.[0]
        if (!run?.state?.histories?.length) return []
        return run.state.histories.map((h: {date: string; state: string}) => ({
            date: moment(h.date),
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
                    executionId: taskExecution.value?.taskRunList
                        .filter((taskRun: TaskRun) =>
                            taskRun.id === props.data.node.taskRun.id &&
                            taskRun.outputs?.executionId,
                        )
                        ?.[0]?.outputs?.executionId,
                },
            }
        }
        return props.data
    })

    const actionConfig = computed(() => {
        const taskType = props.data.node.task?.type as string | undefined
        const runnerType = (props.data.node.task as any)?.taskRunner?.type as string | undefined
        if (!taskType) return undefined
        const customAction = props.customActions?.[taskType] ?? (runnerType ? props.customActions?.[runnerType] : undefined)
        if (customAction) return {config: customAction, eventName: EVENTS.SHOW_CUSTOM_ACTION} as const
        const showDetail = props.showDetails?.[taskType]
        if (showDetail) return {config: showDetail, eventName: EVENTS.SHOW_DETAILS} as const
        return undefined
    })

    const {t} = useI18n()

    const actions = computed<NodeAction[]>(() => {
        const task = props.data.node.task
        const readOnly = props.data.isReadOnly
        const list: NodeAction[] = []

        if (task?.description) {
            list.push({
                key: "description",
                label: t("show description"),
                icon: InformationOutline,
                onClick: () => emit(EVENTS.SHOW_DESCRIPTION, {id: taskId.value, description: task.description}),
            })
        }
        if (task?.runIf) {
            list.push({
                key: "condition",
                label: t("show task condition"),
                icon: SendLock,
                onClick: () => emit(EVENTS.SHOW_CONDITION, {id: taskId.value, task, section: SECTIONS.TASKS}),
            })
        }
        if (taskExecution.value) {
            list.push({
                key: "logs",
                label: t("show task logs"),
                icon: TextBoxSearch,
                onClick: () => emit(EVENTS.SHOW_LOGS, {id: taskId.value, execution: taskExecution.value, taskRuns: taskRuns.value}),
            })
        }
        if (dataWithLink.value.link) {
            list.push({
                key: "open",
                label: t("open"),
                icon: OpenInNew,
                onClick: () => emit(EVENTS.OPEN_LINK, {link: dataWithLink.value.link}),
            })
        }
        if (props.data.expandable) {
            list.push({
                key: "expand",
                label: t("expand"),
                icon: UnfoldMoreHorizontal,
                onClick: () => emit(EVENTS.EXPAND, expandData.value),
            })
        }
        if (!taskExecution.value && !readOnly && props.data.isFlowable) {
            list.push({
                key: "add-error",
                label: t("add error handler"),
                icon: AlertOutline,
                onClick: () => emit(EVENTS.ADD_ERROR, {task}),
            })
        }
        if (!readOnly) {
            list.push({
                key: "edit",
                label: t("edit"),
                icon: Pencil,
                onClick: () => emit(EVENTS.EDIT, {task, section: SECTIONS.TASKS}),
            })
        }
        if (actionConfig.value && task) {
            list.push({
                key: "show-details",
                label: actionConfig.value.config.label || t("show details"),
                icon: EyeOutline,
                onClick: () => onShowDetails(),
            })
        }
        if (!readOnly) {
            list.push({
                key: "delete",
                label: t("delete"),
                icon: Delete,
                danger: true,
                divided: true,
                onClick: () => emit(EVENTS.DELETE, {id: taskId.value, section: SECTIONS.TASKS}),
            })
        }

        return list
    })

    function onShowDetails() {
        if (!actionConfig.value || !props.data.node.task) return
        if (actionConfig.value.eventName === EVENTS.SHOW_CUSTOM_ACTION) {
            emit(EVENTS.SHOW_CUSTOM_ACTION, {task: props.data.node.task, customAction: actionConfig.value.config as CustomActionConfig})
        } else {
            emit(EVENTS.SHOW_DETAILS, {task: props.data.node.task, showDetails: actionConfig.value.config as ShowDetailsConfig})
        }
    }

</script>

<style lang="scss" scoped>
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
    font-size: .8rem;
}

button.playground-button {
    color: var(--ks-white);
    background-color: var(--ks-playground-bg-color);
}

.status-tag {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    padding: 0.4rem;
    border-radius: var(--ks-radius-xs);
    background-color: var(--ks-bg-tag);
    line-height: 1;
}

.status-tag__text {
    font-size: var(--ks-font-size-2xs);
    white-space: nowrap;
}

.details-wrapper {
    font-size: var(--ks-font-size-2xs);

    &:has(> *) {
        border-top: 1px solid var(--ks-border-default);
        background: var(--ks-bg-base);
    }
}

.runner-badge {
    align-self: flex-start;
    padding: 0 var(--ks-spacing-2);
    border-radius: var(--ks-radius-base);
    background-color: var(--ks-bg-tag);
    color: var(--ks-text-info);
    font-size: var(--ks-font-size-2xs);
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.details-slide-enter-active,
.details-slide-leave-active {
    transition: max-height 0.25s ease, opacity 0.25s ease;
    overflow: hidden;
    max-height: 200px;
}

.details-slide-enter-from,
.details-slide-leave-to {
    max-height: 0;
    opacity: 0;
}
</style>
