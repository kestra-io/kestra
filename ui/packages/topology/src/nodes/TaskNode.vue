<template>
    <Handle type="source" :position="sourcePosition" />
    <BasicNode
        :id="id"
        :data="dataWithLink"
        :state="state"
        :class="classes"
        :icons="icons"
        :iconComponent="iconComponent"
        @show-description="emit(EVENTS.SHOW_DESCRIPTION, $event)"
        @expand="emit(EVENTS.EXPAND, expandData)"
        @open-link="emit(EVENTS.OPEN_LINK, $event)"
        @mouseover="emit(EVENTS.MOUSE_OVER, $event)"
        @mouseleave="emit(EVENTS.MOUSE_LEAVE)"
    >
        <template #details>
            <Transition name="details-slide">
                <div v-if="globalShowExtraDetails" class="details-wrapper">
                    <slot name="details" />
                    <div v-if="actionConfig && data.node.task" class="view-details-action">
                        <button
                            type="button"
                            class="view-details-button"
                            aria-label="Show details"
                            @click.stop="onShowDetails()"
                        >
                            Show details
                        </button>
                    </div>
                </div>
            </Transition>
        </template>
        <template #content>
            <ExecutionInformations
                v-if="taskExecution && globalShowExtraDetails"
                :execution="taskExecution"
                :task="data.node.task"
                :color="color"
                :uid="data.node.uid"
                :state="state"
            />

            <template v-if="data.node.task">
                <button v-if="playgroundEnabled && playgroundReadyToStart" type="button" class="playground-button" @click="emit(EVENTS.RUN_TASK, {task: data.node.task})">
                    <KsTooltip style="display: flex;" :content="$t('run task in playground')">
                        <PlayIcon class="button-play-icon" :alt="$t('run task in playground')" />
                    </KsTooltip>
                </button>
                <div
                    v-else-if="state"
                    class="playground-button"
                    :style="{
                        color: `var(--ks-content-${state?.toLowerCase()})`,
                        backgroundColor: `var(--ks-background-${state?.toLowerCase()})`
                    }"
                >
                    <KsTooltip style="display: flex;" :content="iconAlt ? $t(iconAlt) : undefined">
                        <RotatingDots v-if="state === State.RUNNING" :alt="iconAlt ? $t(iconAlt) : undefined" />
                        <CheckCircleOutline v-else-if="state === State.SUCCESS" :style="{color: 'var(--ks-status-success)'}" :alt="iconAlt ? $t(iconAlt) : undefined" />
                        <AlertIcon v-else-if="state === State.WARNING" :alt="iconAlt ? $t(iconAlt) : undefined" />
                        <SkipForwardIcon v-else-if="state === State.SKIPPED" :alt="iconAlt ? $t(iconAlt) : undefined" />
                        <CloseCircleOutline v-else-if="state === State.FAILED" :style="{color: 'var(--ks-status-error)'}" :alt="iconAlt ? $t(iconAlt) : undefined" />
                    </KsTooltip>
                </div>
            </template>
        </template>
        <template #badge-button-before>
            <span
                v-if="data.node.task && data.node.task.runIf"
                class="circle-button"
                :style="{backgroundColor: 'var(--ks-node-warning)'}"
                @click="emit(EVENTS.SHOW_CONDITION, {id: taskId, task: data.node.task, section: SECTIONS.TASKS})"
            >
                <KsTooltip :content="$t('show task condition')">
                    <SendLock class="button-icon" alt="Show condition" />
                </KsTooltip>
            </span>
            <span
                v-if="taskExecution"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${color})`}"
                @click="emit(EVENTS.SHOW_LOGS, {id: taskId, execution: taskExecution, taskRuns})"
            >
                <KsTooltip :content="$t('show task logs')">
                    <TextBoxSearch class="button-icon" alt="Show logs" />
                </KsTooltip>
            </span>

            <span
                v-if="!taskExecution && !data.isReadOnly && data.isFlowable"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${color})`}"
                @click="emit(EVENTS.ADD_ERROR, {task: data.node.task})"
            >
                <KsTooltip :content="$t('add error handler')">
                    <AlertOutline class="button-icon" alt="Add error handler" />
                </KsTooltip>
            </span>
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import {State, KsTooltip, SECTIONS} from "@kestra-io/design-system"
    import {type CustomActionConfig, type ShowDetailsConfig, EVENTS} from "../utils/constants"
    import ExecutionInformations from "../misc/ExecutionInformations.vue"
    import * as Utils from "../utils/utils"
    import BasicNode from "./BasicNode.vue"
    import {
        EXECUTION_INJECTION_KEY,
        SUBFLOWS_EXECUTIONS_INJECTION_KEY,
        SHOW_EXTRA_DETAILS_INJECTION_KEY,
    } from "../injectionKeys"

    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
    import SendLock from "vue-material-design-icons/SendLock.vue"
    import PlayIcon from "vue-material-design-icons/Play.vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
    import AlertIcon from "vue-material-design-icons/Alert.vue"
    import SkipForwardIcon from "vue-material-design-icons/SkipForward.vue"
    import RotatingDots from "../assets/icons/RotatingDots.vue"
    

    interface TaskType {
        id: string;
        type: string;
        default: null;
        runIf?: unknown;
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

    const color = computed(() => props.data.color ?? "primary")

    const taskId = computed(() => Utils.afterLastDot(props.id))

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
        if (!taskType) return undefined
        const customAction = props.customActions?.[taskType]
        if (customAction) return {config: customAction, eventName: EVENTS.SHOW_CUSTOM_ACTION} as const
        const showDetail = props.showDetails?.[taskType]
        if (showDetail) return {config: showDetail, eventName: EVENTS.SHOW_DETAILS} as const
        return undefined
    })

    function onShowDetails() {
        if (!actionConfig.value || !props.data.node.task) return
        if (actionConfig.value.eventName === EVENTS.SHOW_CUSTOM_ACTION) {
            emit(EVENTS.SHOW_CUSTOM_ACTION, {task: props.data.node.task, customAction: actionConfig.value.config as CustomActionConfig})
        } else {
            emit(EVENTS.SHOW_DETAILS, {task: props.data.node.task, showDetails: actionConfig.value.config as ShowDetailsConfig})
        }
    }

    const iconAlt = computed(() => {
        if (state.value === State.RUNNING) {
            return "task is running"
        }
        if (state.value === State.SUCCESS) {
            return "task was successful"
        }
        if (state.value === State.WARNING) {
            return "task sent a warning"
        }
        if (state.value === State.SKIPPED) {
            return "task was skipped"
        }
        if (state.value === State.FAILED) {
            return "task failed"
        }
        return undefined
    })
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

.view-details-action {
    display: flex;
    justify-content: flex-end;
    margin-top: 6px;
    padding: 0 8px 8px;
}

.view-details-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    max-width: 100%;
    box-sizing: border-box;
    appearance: none;
    margin: 0;
    padding: 4px 10px;
    border: 1px solid var(--ks-border-primary);
    border-radius: 999px;
    background-color: var(--ks-bg-surface);
    color: var(--ks-text-secondary);
    cursor: pointer;
    font: inherit;
    font-size: 0.75rem;
    font-weight: 500;
    line-height: 1.2;
    white-space: nowrap;
    text-transform: none;
    box-shadow: none;
    transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;

    &:hover {
        border-color: var(--ks-border-active);
        background-color: var(--ks-button-background-secondary-hover);
        color: var(--ks-text-primary);
    }

    &:focus-visible {
        outline: 2px solid var(--ks-border-active);
        outline-offset: 2px;
    }
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
