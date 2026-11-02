<template>
    <KsDialog
        v-model="open"
        :title="taskId"
        :fullscreen="isFullscreen"
        :showClose="false"
        :width="POPUP_LAYOUT.width"
        :top="POPUP_LAYOUT.top"
        fill
        appendToBody
        destroyOnClose
        data-test="task-run-popup"
        @closed="emit('closed')"
    >
        <template #header>
            <header class="popup-header">
                <div class="popup-identity">
                    <span class="popup-icon">
                        <TaskIcon :cls="taskType" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon />
                    </span>
                    <div class="popup-titles">
                        <div class="popup-title-row">
                            <KsText tag="strong" class="popup-title" data-test="task-run-popup-title">{{ taskId }}</KsText>
                            <KsExecutionStatus size="small" :status="currentTaskRun.state.current" data-test="task-run-popup-state" />
                        </div>
                        <div class="popup-meta">
                            <code v-if="taskType" data-test="task-run-popup-type">{{ taskType }}</code>
                            <span v-if="histories.length" class="popup-meta-item" data-test="task-run-popup-duration">
                                <KsIcon size="xs"><TimerOutline /></KsIcon>
                                <Duration :histories="histories" :interval="100" :attemptCount="attemptCount" :subject="taskId" />
                            </span>
                            <span class="popup-meta-item" data-test="task-run-popup-attempt">
                                <KsIcon size="xs"><SourceBranch /></KsIcon>
                                {{ $t("attempt") }} {{ attemptCount }}
                            </span>
                        </div>
                    </div>
                </div>

                <div class="popup-actions">
                    <KsDropdown v-if="currentTaskRuns.length > 1" trigger="click" :persistent="true">
                        <KsButton data-test="task-run-popup-picker">
                            {{ currentIterationLabel }}
                            <KsIcon size="xs"><ChevronDown /></KsIcon>
                        </KsButton>
                        <template #dropdown>
                            <KsDropdownMenu>
                                <TaskRunIterationSelector
                                    :taskRuns="currentTaskRuns"
                                    :selectedId="selectedTaskRunId"
                                    @select="selectedTaskRunId = $event"
                                />
                            </KsDropdownMenu>
                        </template>
                    </KsDropdown>

                    <span class="popup-action">
                        <Restart
                            :key="`restart-${runKey}`"
                            isReplay
                            tooltipPosition="bottom"
                            :execution="execution"
                            :taskRun="currentTaskRun"
                            :attemptIndex="attemptIndex"
                            @follow="emit('follow', $event)"
                        />
                    </span>
                    <span class="popup-action">
                        <ChangeStatus
                            :key="`change-status-${runKey}`"
                            labeled
                            :execution="execution"
                            :taskRun="currentTaskRunWithExecution"
                            :attemptIndex="attemptIndex"
                            @follow="emit('follow', $event)"
                        />
                    </span>
                    <span class="popup-action">
                        <TaskEdit
                            v-if="canReadFlow"
                            labeled
                            readOnly
                            :label="$t('task_run_popup.task_source')"
                            :taskId="taskId"
                            section="tasks"
                            :flowId="execution.flowId"
                            :namespace="execution.namespace"
                            :revision="execution.flowRevision"
                            :flowSource="flowSource"
                        />
                    </span>
                    <span class="popup-action">
                        <WorkerInfo
                            v-if="hasWorkerId"
                            component="KsButton"
                            labeled
                            :label="$t('task_run_popup.worker_info')"
                            :taskRun="currentTaskRun"
                        />
                    </span>
                    <span class="popup-action">
                        <Interrupt
                            :key="`interrupt-${runKey}`"
                            labeled
                            :execution="execution"
                            :taskRun="currentTaskRun"
                            :attemptIndex="attemptIndex"
                            @follow="emit('follow', $event)"
                        />
                    </span>

                    <span class="popup-action">
                        <TaskRunActions
                            overflow
                            :taskRun="currentTaskRun"
                            :taskRuns="taskRuns"
                            :execution="execution"
                            :flow="flow"
                            :taskType="taskType"
                            :attemptIndex="attemptIndex"
                            :nodeActions="overflowActions"
                            @follow="emit('follow', $event)"
                            @update-logs="onLogsDeleted"
                            @dismiss="close"
                        />
                    </span>

                    <span class="popup-divider" role="separator" />

                    <span class="popup-action">
                        <KsIconButton
                            :tooltip="fullscreenLabel"
                            :aria-label="fullscreenLabel"
                            :aria-pressed="isFullscreen"
                            data-test="task-run-popup-fullscreen"
                            @click="isFullscreen = !isFullscreen"
                        >
                            <component :is="isFullscreen ? FullscreenExit : Fullscreen" />
                        </KsIconButton>
                    </span>
                    <span class="popup-action">
                        <KsIconButton
                            :tooltip="$t('close')"
                            :aria-label="$t('close')"
                            data-test="task-run-popup-close"
                            @click="close"
                        >
                            <Close />
                        </KsIconButton>
                    </span>
                </div>
            </header>
        </template>

        <div class="popup-content" :class="{'popup-content--fullscreen': isFullscreen}" :style="{'--popup-content-height': POPUP_LAYOUT.contentHeight}">
            <div v-if="description" class="popup-description" data-test="task-run-popup-description">
                <KsScrollbar :maxHeight="96">
                    <KsMarkdown :content="description" />
                </KsScrollbar>
            </div>

            <KsTabs v-model="tab" type="box" class="popup-tabs" paneScroll data-test="task-run-popup-tabs">
                <KsTabPane name="metrics" lazy>
                    <template #label>
                        <TaskRunPopupTabLabel
                            :label="$t('metrics')"
                            :status="metricsCount.status.value"
                            :count="metricsCount.value.value"
                            data-test="task-run-popup-tab-metrics"
                        />
                    </template>
                    <div class="tab-body">
                        <MetricsTable :key="currentTaskRun.id" :taskRunId="currentTaskRun.id" :execution="execution">
                            <template #empty>
                                <KsEmpty :imageSize="96" :description="$t('no metrics available')" />
                            </template>
                        </MetricsTable>
                    </div>
                </KsTabPane>

                <KsTabPane name="logs" lazy>
                    <template #label>
                        <TaskRunPopupTabLabel
                            :label="$t('logs')"
                            :status="logsCount.status.value"
                            :count="logsCount.value.value"
                            data-test="task-run-popup-tab-logs"
                        />
                    </template>
                    <div class="tab-body">
                        <TaskRunLogs
                            :key="`${currentTaskRun.id}-${logsRefresh}`"
                            :executionId="execution.id"
                            :taskRunIds="logTaskRunIds"
                            hideTaskHeader
                            @follow="emit('follow', $event)"
                        />
                        <KsText v-if="hiddenChildCount > 0" size="small" class="tab-note" data-test="task-run-popup-logs-capped">
                            {{ $t("task_run_popup.child_runs_capped", {count: MAX_CHILD_LOG_RUNS}) }}
                        </KsText>
                    </div>
                </KsTabPane>

                <KsTabPane name="outputs" lazy class="popup-pane--fill">
                    <template #label>
                        <TaskRunPopupTabLabel
                            :label="$t('outputs')"
                            :status="outputs.status.value"
                            :count="outputsCount"
                            data-test="task-run-popup-tab-outputs"
                        />
                    </template>
                    <div class="tab-body tab-body--fill">
                        <KsSkeleton v-if="outputs.status.value === 'loading'" animated :rows="4" data-test="task-run-popup-outputs-loading" />
                        <KsAlert v-else-if="outputs.status.value === 'error'" type="error" :closable="false" data-test="task-run-popup-outputs-error">
                            {{ $t("task_run_popup.load_error") }}
                            <KsButton size="small" @click="dataReload">
                                {{ $t("task_run_popup.retry") }}
                            </KsButton>
                        </KsAlert>
                        <KsEmpty
                            v-else-if="outputsCount === 0"
                            class="tab-empty"
                            :imageSize="96"
                            :description="$t('no outputs available')"
                            data-test="task-run-popup-outputs-empty"
                        />
                        <ExecutionVariableExplorer
                            v-else
                            :key="currentTaskRun.id"
                            :scope="{execution, taskId, outputs: outputs.value.value}"
                            sidebarWidth="31%"
                            debugWidth="25%"
                        />
                    </div>
                </KsTabPane>

                <KsTabPane name="artifacts" lazy class="popup-pane--fill">
                    <template #label>
                        <TaskRunPopupTabLabel
                            :label="$t('execution_artifacts.title')"
                            :icon="DiamondStone"
                            :status="artifactsStatus"
                            :count="taskArtifacts.length"
                            data-test="task-run-popup-tab-artifacts"
                        />
                    </template>
                    <div class="tab-body tab-body--fill">
                        <KsSkeleton v-if="artifactsStatus === 'loading'" animated :rows="4" data-test="task-run-popup-artifacts-loading" />
                        <KsEmpty
                            v-else-if="!selectedArtifact"
                            class="tab-empty"
                            :imageSize="96"
                            :description="$t('task_run_popup.no_artifacts')"
                            data-test="task-run-popup-artifacts-empty"
                        />
                        <ExecutionArtifactViewer
                            v-else
                            :artifact="selectedArtifact"
                            :siblings="taskArtifacts"
                            :execution="execution"
                            :taskRunId="currentTaskRun.id"
                            @select="selectedArtifactKey = $event"
                        />
                    </div>
                </KsTabPane>
            </KsTabs>
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, inject, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {dayjs} from "@kestra-io/design-system"
    import {Duration, type NodeAction} from "@kestra-io/topology"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import DiamondStone from "vue-material-design-icons/DiamondStone.vue"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import FullscreenExit from "vue-material-design-icons/FullscreenExit.vue"
    import SourceBranch from "vue-material-design-icons/SourceBranch.vue"
    import TimerOutline from "vue-material-design-icons/TimerOutline.vue"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {usePluginsStore} from "../../stores/plugins"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import type {Flow} from "../../stores/flow"
    import TaskIcon from "../plugins/TaskIcon.vue"
    import TaskEdit from "../flows/TaskEdit.vue"
    import Restart from "./overview/components/actions/Restart.vue"
    import ChangeStatus from "./ChangeStatus.vue"
    import Interrupt from "./Interrupt.vue"
    import WorkerInfo from "./WorkerInfo.vue"
    import MetricsTable from "./MetricsTable.vue"
    import TaskRunActions from "./TaskRunActions.vue"
    import TaskRunLogs from "./TaskRunLogs.vue"
    import TaskRunIterationSelector from "./TaskRunIterationSelector.vue"
    import TaskRunPopupTabLabel from "./TaskRunPopupTabLabel.vue"
    import ExecutionArtifactViewer from "./ExecutionArtifactViewer.vue"
    import ExecutionVariableExplorer from "./outputs/ExecutionVariableExplorer.vue"
    import {EXECUTION_ARTIFACTS_KEY} from "./composables/useExecutionArtifacts"
    import {useSelectedTaskRun} from "./composables/useSelectedTaskRun"
    import {useTaskRunPopupData} from "./composables/useTaskRunPopupData"
    import {iterationLabel} from "./iterationLabel"

    export type TaskRunPopupTab = "metrics" | "logs" | "outputs" | "artifacts"

    const props = withDefaults(defineProps<{
        execution: Execution
        taskId: string
        taskType?: string
        description?: string
        flow?: Pick<Flow, "tasks"> & Partial<Pick<Flow, "source">>
        nodeActions?: NodeAction[]
        initialTab?: TaskRunPopupTab
    }>(), {
        taskType: undefined,
        description: undefined,
        flow: undefined,
        nodeActions: () => [],
        initialTab: "logs",
    })

    const POPUP_LAYOUT = {width: "1280px", top: "12vh", contentHeight: "65vh"} as const

    const open = defineModel<boolean>({default: false})

    const emit = defineEmits<{
        follow: [event: unknown]
        closed: []
    }>()

    const MAX_CHILD_LOG_RUNS = 20

    const {t} = useI18n()
    const authStore = useAuthStore()
    const pluginsStore = usePluginsStore()
    pluginsStore.fetchIcons()
    const executionsStore = useExecutionsStore()
    const artifactsContext = inject(EXECUTION_ARTIFACTS_KEY, undefined)

    const tab = ref<TaskRunPopupTab>(props.initialTab)
    const isFullscreen = ref(false)
    const logsRefresh = ref(0)
    const selectedArtifactKey = ref<string>()

    watch(open, (isOpen) => {
        if (!isOpen) return
        tab.value = props.initialTab
        isFullscreen.value = false
        selectedArtifactKey.value = undefined
    })

    watch(() => props.initialTab, (initialTab) => {
        if (open.value) tab.value = initialTab
    })

    const taskRuns = computed(() => (props.execution.taskRunList ?? []).filter((run) => run.taskId === props.taskId))

    const {
        currentTaskRuns,
        selectedTaskRunId,
        currentTaskRun,
        currentTaskRunWithExecution,
    } = useSelectedTaskRun({
        execution: () => props.execution,
        taskRun: () => taskRuns.value[0],
        taskRuns: () => taskRuns.value,
    })

    const attemptCount = computed(() => currentTaskRun.value.attempts?.length || 1)
    const attemptIndex = computed(() => attemptCount.value - 1)
    const runKey = computed(() => `${currentTaskRun.value.id}-${attemptIndex.value}-${currentTaskRun.value.attempts?.[attemptIndex.value]?.state.startDate}`)

    const histories = computed(() =>
        (currentTaskRun.value.state.histories ?? []).map((history) => ({date: dayjs(history.date), state: history.state})),
    )

    const currentIterationLabel = computed(() =>
        iterationLabel(t, currentTaskRun.value, currentTaskRuns.value.findIndex((run) => run.id === currentTaskRun.value.id) + 1),
    )

    const childTaskRuns = computed(() =>
        (props.execution.taskRunList ?? []).filter((run) => run.parentTaskRunId === currentTaskRun.value.id),
    )
    const logChildTaskRunIds = computed(() => childTaskRuns.value.slice(0, MAX_CHILD_LOG_RUNS).map((run) => run.id))
    const hiddenChildCount = computed(() => Math.max(0, childTaskRuns.value.length - MAX_CHILD_LOG_RUNS))
    const logTaskRunIds = computed(() => [currentTaskRun.value.id, ...logChildTaskRunIds.value])

    const isRootExecution = computed(() => executionsStore.execution?.id === props.execution.id)

    const flowSource = computed(() => isRootExecution.value ? props.flow?.source : undefined)

    const canReadFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.VIEW, props.execution.namespace) === true,
    )

    const hasWorkerId = computed(() =>
        currentTaskRun.value.attempts?.some((attempt: {workerId?: string | null}) => attempt.workerId != null) === true,
    )

    const fullscreenLabel = computed(() => t(isFullscreen.value ? "execution_artifacts.exit_fullscreen" : "execution_artifacts.fullscreen"))

    const {metricsCount, logsCount, outputs, outputsCount, reload: dataReload} = useTaskRunPopupData({
        enabled: () => open.value,
        executionId: () => props.execution.id,
        taskId: () => props.taskId,
        taskRunId: () => currentTaskRun.value.id,
        logTaskRunId: () => currentTaskRun.value.id,
        logAttemptNumber: () => attemptIndex.value,
        logChildTaskRunIds: () => logChildTaskRunIds.value,
        taskRunState: () => currentTaskRun.value.state.current,
    })

    const taskArtifacts = computed(() => isRootExecution.value
        ? (artifactsContext?.artifacts.value ?? []).filter((artifact) => artifact.taskId === props.taskId)
        : [])

    const artifactsStatus = computed(() => artifactsContext && !artifactsContext.manifestReady.value ? "loading" : "ready")

    const selectedArtifact = computed(() =>
        taskArtifacts.value.find((artifact) => artifact.key === selectedArtifactKey.value) ?? taskArtifacts.value[0],
    )

    const overflowActions = computed(() => props.nodeActions.map((nodeAction) => ({
        ...nodeAction,
        onClick: () => {
            close()
            nodeAction.onClick()
        },
    })))

    function close() {
        open.value = false
    }

    function onLogsDeleted() {
        logsRefresh.value++
        dataReload()
    }
</script>

<style scoped>
    .popup-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        margin-inline: calc(var(--ks-spacing-4) * -1);
        padding: 0 var(--ks-spacing-4) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);
    }

    .popup-identity {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        min-width: 0;
        flex: 1 1 auto;
    }

    .popup-icon {
        flex: 0 0 auto;
        width: var(--ks-spacing-7);
        height: var(--ks-spacing-7);
        padding: var(--ks-spacing-1);
        box-sizing: border-box;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        background-color: var(--ks-topology-icon-bg);
    }

    .popup-titles {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .popup-title {
        font-size: var(--ks-font-size-lg);
        font-weight: var(--ks-font-weight-semibold);
    }

    .popup-title-row,
    .popup-meta {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .popup-meta {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
        font-weight: var(--ks-font-weight-regular);
    }

    .popup-meta-item {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .popup-actions {
        display: flex;
        align-items: center;
        flex: 0 0 auto;
        gap: var(--ks-spacing-2);
    }

    .popup-action {
        display: contents;
    }

    .popup-divider {
        align-self: stretch;
        margin-block: var(--ks-spacing-1);
        border-left: 1px solid var(--ks-border-default);
    }

    .popup-content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        height: var(--popup-content-height);
        min-height: 0;

        &.popup-content--fullscreen {
            height: auto;
            flex: 1;
        }
    }

    .popup-description {
        flex: 0 0 auto;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
    }

    .popup-tabs {
        flex: 1;
        min-height: 0;
    }

    .tab-note {
        display: block;
        padding-top: var(--ks-spacing-2);
        color: var(--ks-text-secondary);
    }

    .tab-empty {
        margin: auto;
    }

    .popup-pane--fill {
        height: 100%;
    }

    .tab-body {
        padding-block: var(--ks-spacing-4);

        &.tab-body--fill {
            display: flex;
            box-sizing: border-box;
            height: 100%;
            min-height: 0;
            padding-block: var(--ks-spacing-4) 0;
        }
    }
</style>
