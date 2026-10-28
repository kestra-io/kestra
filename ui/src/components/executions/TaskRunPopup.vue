<template>
    <KsDialog
        v-model="open"
        :title="taskId"
        :fullscreen="isFullscreen"
        :showClose="false"
        width="1200px"
        top="6vh"
        fill
        appendToBody
        destroyOnClose
        data-test="task-run-popup"
    >
        <template #header>
            <header class="popup-header">
                <div class="popup-identity">
                    <span class="popup-icon">
                        <TaskIcon :cls="taskType" :icons="pluginsStore.icons" :loadIcon="pluginsStore.loadIcon" onlyIcon />
                    </span>
                    <div class="popup-titles">
                        <div class="popup-title-row">
                            <KsText tag="strong" data-test="task-run-popup-title">{{ taskId }}</KsText>
                            <KsExecutionStatus size="small" :status="currentTaskRun.state.current" data-test="task-run-popup-state" />
                        </div>
                        <div class="popup-meta">
                            <code v-if="taskType" data-test="task-run-popup-type">{{ taskType }}</code>
                            <span v-if="duration" class="popup-meta-item" data-test="task-run-popup-duration">
                                <KsIcon size="xs"><TimerOutline /></KsIcon>
                                {{ duration }}
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
                        <KsButton size="small" data-test="task-run-popup-picker">
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

                    <Restart
                        :key="`restart-${runKey}`"
                        isReplay
                        tooltipPosition="bottom"
                        :execution="execution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="attemptIndex"
                        @follow="emit('follow', $event)"
                    />
                    <ChangeStatus
                        :key="`change-status-${runKey}`"
                        labeled
                        :execution="execution"
                        :taskRun="currentTaskRunWithExecution"
                        :attemptIndex="attemptIndex"
                        @follow="emit('follow', $event)"
                    />
                    <TaskEdit
                        v-if="canReadFlow"
                        labeled
                        readOnly
                        :taskId="taskId"
                        section="tasks"
                        :flowId="execution.flowId"
                        :namespace="execution.namespace"
                        :revision="execution.flowRevision"
                        :flowSource="flowSource"
                    />
                    <WorkerInfo
                        v-if="hasWorkerId"
                        component="KsButton"
                        labeled
                        :taskRun="currentTaskRun"
                    />
                    <Interrupt
                        :key="`interrupt-${runKey}`"
                        labeled
                        :execution="execution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="attemptIndex"
                        @follow="emit('follow', $event)"
                    />

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

                    <KsIconButton
                        :tooltip="fullscreenLabel"
                        :aria-label="fullscreenLabel"
                        :aria-pressed="isFullscreen"
                        data-test="task-run-popup-fullscreen"
                        @click="isFullscreen = !isFullscreen"
                    >
                        <component :is="isFullscreen ? FullscreenExit : Fullscreen" />
                    </KsIconButton>
                    <KsIconButton
                        :tooltip="$t('close')"
                        :aria-label="$t('close')"
                        data-test="task-run-popup-close"
                        @click="close"
                    >
                        <Close />
                    </KsIconButton>
                </div>
            </header>
        </template>

        <div class="popup-content" :class="{'popup-content--fullscreen': isFullscreen}">
            <div v-if="description" class="popup-description" data-test="task-run-popup-description">
                <KsScrollbar :maxHeight="96">
                    <KsMarkdown :content="description" />
                </KsScrollbar>
            </div>

            <KsTabs v-model="tab" class="popup-tabs" paneScroll data-test="task-run-popup-tabs">
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
                        <KsEmpty
                            v-if="logsCount.status.value === 'ready' && logsCount.value.value === 0"
                            :imageSize="96"
                            :description="$t('task_run_popup.no_logs')"
                            data-test="task-run-popup-logs-empty"
                        />
                        <TaskRunLogs
                            v-else
                            :key="`${currentTaskRun.id}-${logsRefresh}`"
                            :executionId="execution.id"
                            :taskRunIds="logTaskRunIds"
                            @follow="emit('follow', $event)"
                        />
                    </div>
                </KsTabPane>

                <KsTabPane name="outputs" lazy>
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
                            :imageSize="96"
                            :description="$t('no outputs available')"
                            data-test="task-run-popup-outputs-empty"
                        />
                        <ExecutionVariableExplorer
                            v-else
                            :key="currentTaskRun.id"
                            :scope="{execution, taskId, outputs: outputs.value.value}"
                        />
                    </div>
                </KsTabPane>

                <KsTabPane name="artifacts" lazy>
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
                            :imageSize="96"
                            :description="$t('task_run_popup.no_artifacts')"
                            data-test="task-run-popup-artifacts-empty"
                        />
                        <ExecutionArtifactViewer
                            v-else
                            :artifact="selectedArtifact"
                            :siblings="taskArtifacts"
                            :execution="execution"
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
    import {durationUtils} from "@kestra-io/design-system"
    import {type NodeAction} from "@kestra-io/topology"
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

    const open = defineModel<boolean>({default: false})

    const emit = defineEmits<{
        follow: [event: unknown]
    }>()

    const GRAPH_AFFECTING_ACTIONS = ["open", "expand"]

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

    const duration = computed(() => {
        const iso = currentTaskRun.value.state.duration
        return iso ? durationUtils.humanDuration(durationUtils.duration(iso)) : undefined
    })

    const currentIterationLabel = computed(() =>
        iterationLabel(t, currentTaskRun.value, currentTaskRuns.value.findIndex((run) => run.id === currentTaskRun.value.id) + 1),
    )

    const logTaskRunIds = computed(() => {
        const children = (props.execution.taskRunList ?? []).filter((run) => run.parentTaskRunId === currentTaskRun.value.id)
        return [currentTaskRun.value.id, ...children.map((run) => run.id)]
    })

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
        logTaskRunIds: () => logTaskRunIds.value,
        taskRunState: () => currentTaskRun.value.state.current,
    })

    const taskArtifacts = computed(() => isRootExecution.value
        ? (artifactsContext?.artifacts.value ?? []).filter((artifact) => artifact.taskId === props.taskId)
        : [])

    const artifactsStatus = computed(() => artifactsContext && !artifactsContext.manifestReady.value ? "loading" : "ready")

    const selectedArtifact = computed(() =>
        taskArtifacts.value.find((artifact) => artifact.key === selectedArtifactKey.value) ?? taskArtifacts.value[0],
    )

    const overflowActions = computed(() => props.nodeActions.map((nodeAction) =>
        GRAPH_AFFECTING_ACTIONS.includes(nodeAction.key)
            ? {...nodeAction, onClick: () => {
                close()
                nodeAction.onClick()
            }}
            : nodeAction,
    ))

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
        flex-wrap: wrap;
        gap: var(--ks-spacing-3);
    }

    .popup-identity {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        min-width: 0;
    }

    .popup-icon {
        flex: 0 0 auto;
        width: 2.5rem;
        height: 2.5rem;
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

    .popup-title-row,
    .popup-meta {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .popup-meta {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
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
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
    }

    .popup-content {
        --popup-fill-height: calc(65vh - 8rem);
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        height: 65vh;
        min-height: 0;

        &.popup-content--fullscreen {
            --popup-fill-height: calc(100vh - 14rem);
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

    .tab-body {
        padding-block: var(--ks-spacing-3);

        &.tab-body--fill {
            display: flex;
            height: var(--popup-fill-height);
            min-height: 0;
            padding-block: var(--ks-spacing-3) 0;
        }
    }
</style>
