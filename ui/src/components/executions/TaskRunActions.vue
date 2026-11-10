<template>
    <KsDropdown trigger="click" :persistent="true">
        <KsIconButton
            v-if="overflow"
            :aria-label="$t('more actions')"
            data-test="task-run-overflow"
        >
            <DotsVertical />
        </KsIconButton>
        <KsButton v-else type="default" class="task-run-buttons" :aria-label="$t('actions')">
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <TaskRunIterationSelector
                    v-if="!overflow && currentTaskRuns.length > 1"
                    :taskRuns="currentTaskRuns"
                    :selectedId="selectedTaskRunId"
                    @select="selectedTaskRunId = $event"
                />

                <KsDropdownItem
                    v-if="selectedAttempt?.state.current === 'FAILED'"
                    @click="fixErrorWithAi"
                >
                    <span class="fix-with-ai">
                        <AiIcon />
                        <span>{{ $t('fix_with_ai') }}</span>
                    </span>
                </KsDropdownItem>
                <SubFlowLink
                    v-if="subflowExecutionId"
                    component="KsDropdownItem"
                    tabExecution="logs"
                    :executionId="subflowExecutionId"
                    @click="emit('dismiss')"
                />
                <KsDropdownItem
                    v-if="isLoop"
                    :icon="Repeat"
                    @click="openIterations"
                >
                    {{ $t("iterations") }}
                </KsDropdownItem>

                <template v-if="!overflow">
                    <Metrics
                        :taskRun="currentTaskRun"
                        :execution="execution"
                    />

                    <Outputs
                        :taskRun="currentTaskRun"
                        :executionId="execution.id"
                        :execution="execution"
                    />

                    <Restart
                        component="KsDropdownItem"
                        :key="`restart-${currentTaskRun.id}-${currentAttemptIndex}-${selectedAttempt?.state.startDate}`"
                        isReplay
                        tooltipPosition="left"
                        :execution="execution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="currentAttemptIndex"
                        @follow="emit('follow', $event)"
                    />

                    <ChangeStatus
                        component="KsDropdownItem"
                        :key="`change-status-${currentTaskRun.id}-${currentAttemptIndex}-${selectedAttempt?.state.startDate}`"
                        :execution="execution"
                        :taskRun="currentTaskRunWithExecution"
                        :attemptIndex="currentAttemptIndex"
                        @follow="emit('follow', $event)"
                    />

                    <TaskEdit
                        v-if="canReadFlow"
                        :readOnly="true"
                        component="KsDropdownItem"
                        :taskId="currentTaskRun.taskId"
                        section="tasks"
                        :flowId="execution.flowId"
                        :namespace="execution.namespace"
                        :revision="execution.flowRevision"
                        :flowSource="flow?.source"
                    />
                </template>

                <KsDropdownItem
                    :icon="Download"
                    data-test="task-run-download-logs"
                    @click="downloadContent(currentTaskRun.id)"
                >
                    {{ $t("download logs") }}
                </KsDropdownItem>
                <KsDropdownItem
                    :icon="Copy"
                    data-test="task-run-copy-logs"
                    @click="copyContent(currentTaskRun.id)"
                >
                    {{ $t("copy logs") }}
                </KsDropdownItem>
                <KsDropdownItem
                    :icon="Delete"
                    data-test="task-run-delete-logs"
                    @click="deleteLogs(currentTaskRun)"
                >
                    {{ $t("delete logs") }}
                </KsDropdownItem>
                <template v-if="!overflow">
                    <WorkerInfo
                        component="KsDropdownItem"
                        v-if="hasWorkerId"
                        :taskRun="currentTaskRun"
                        @follow="emit('follow', $event)"
                    />

                    <Interrupt
                        component="KsDropdownItem"
                        :key="`interrupt-${currentTaskRun.id}-${currentAttemptIndex}-${selectedAttempt?.state.startDate}`"
                        :execution="execution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="currentAttemptIndex"
                        @follow="emit('follow', $event)"
                    />
                </template>

                <NodeMenuItem
                    v-for="action in nodeActions"
                    :key="action.key"
                    :action="action"
                />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import Copy from "vue-material-design-icons/ContentCopy.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Repeat from "vue-material-design-icons/Repeat.vue"

    import type {LogEntry} from "@kestra-io/kestra-sdk"
    import type {Execution} from "../../stores/executions"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import type {Flow} from "../../stores/flow"

    import * as Utils from "../../utils/utils"
    import {findTaskById, LOOP_TASK_TYPE} from "../../utils/flowUtils"
    import {useToast} from "../../utils/toast"
    import {handledIf} from "../../utils/kestraHttp"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useCoreStore} from "../../stores/core"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    
    import Restart from "./overview/components/actions/Restart.vue"
    import Metrics from "./Metrics.vue"
    import ChangeStatus from "./ChangeStatus.vue"
    import Interrupt from "./Interrupt.vue"
    import Outputs from "./Outputs.vue"
    import WorkerInfo from "./WorkerInfo.vue"
    import TaskEdit from "../flows/TaskEdit.vue"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import AiIcon from "../ai/AiIcon.vue"
    import {NodeMenuItem, type NodeAction} from "@kestra-io/topology"
    import {loopIterationsRoute} from "../../utils/executionUtils"
    import TaskRunIterationSelector from "./TaskRunIterationSelector.vue"
    import {useSelectedTaskRun} from "./composables/useSelectedTaskRun"
    import {iterationLabel as labelFor} from "./iterationLabel"

    const props = withDefaults(defineProps<{
        taskRun: ExecutionTaskRun
        taskRuns?: ExecutionTaskRun[]
        execution: Execution
        flow?: Pick<Flow, "tasks"> & Partial<Pick<Flow, "source">>
        taskType?: string
        attemptIndex?: number
        forcedAttemptNumber?: number
        attemptLogs?: LogEntry[]
        nodeActions?: NodeAction[]
        overflow?: boolean
    }>(), {
        flow: undefined,
        attemptIndex: 0,
        forcedAttemptNumber: undefined,
        attemptLogs: () => [],
        nodeActions: () => [],
        overflow: false,
    })

    const emit = defineEmits<{
        follow: [event: unknown]
        "update-logs": [executionId: string]
        dismiss: []
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const miscStore = useMiscStore()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const {
        currentTaskRuns,
        selectedTaskRunId,
        currentTaskRun,
        currentTaskRunWithExecution,
        currentAttemptIndex,
        selectedAttempt,
    } = useSelectedTaskRun({
        execution: () => props.execution,
        taskRun: () => props.taskRun,
        taskRuns: () => props.taskRuns,
        attemptIndex: () => props.attemptIndex,
        forcedAttemptNumber: () => props.forcedAttemptNumber,
    })

    const subflowExecutionId = computed(() => {
        const executionId = currentTaskRun.value.outputs?.executionId
        return typeof executionId === "string" ? executionId : undefined
    })
    const isLoop = computed(() => (props.taskType ?? findTaskById(props.flow, props.taskRun.taskId)?.type) === LOOP_TASK_TYPE)

    const hasWorkerId = computed<boolean>(() =>
        currentTaskRun.value.attempts?.find((attempt: { workerId?: string | null }) => attempt.workerId != null) !== undefined,
    )

    const canReadFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.VIEW, String(route.params.namespace)),
    )

    function openIterations() {
        emit("dismiss")
        router.push(loopIterationsRoute(props.execution, props.taskRun.taskId))
    }

    function downloadNameFor(currentTaskRunId: string): string {
        const now = new Date()
        const pad = (n: number) => String(n).padStart(2, "0")
        const formatted = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
        return `kestra-execution-${formatted}-${props.execution.id}-${currentTaskRunId}.log`
    }

    function downloadContent(currentTaskRunId: string) {
        executionsStore.downloadLogs({
            executionId: props.execution.id,
            filters: [{field: "taskRunId", operation: "EQUALS", value: currentTaskRunId}],
        }).then((response: unknown) => {
            Utils.downloadUrl(window.URL.createObjectURL(new Blob([response as BlobPart])), downloadNameFor(currentTaskRunId))
        })
    }

    function copyContent(currentTaskRunId: string) {
        executionsStore.downloadLogs({
            executionId: props.execution.id,
            filters: [{field: "taskRunId", operation: "EQUALS", value: currentTaskRunId}],
        }).then((response: unknown) => {
            Utils.copy(response as string).then(() => {
                coreStore.message = {
                    variant: "success",
                    title: t("success"),
                    content: t("copied_logs_to_clipboard"),
                }
            })
        })
    }

    function iterationLabel(run: {id: string; value?: unknown}) {
        return labelFor(t, run, currentTaskRuns.value.findIndex(r => r.id === run.id) + 1)
    }

    function deleteLogs(run: { id: string; value?: unknown }) {
        const label = iterationLabel(run)

        toast.confirm(
            currentTaskRuns.value.length > 1 ? t("delete_log_iteration", {iteration: label}) : t("delete_log"),
            async () => {
                await executionsStore.deleteLogs({
                    executionId: props.execution.id,
                    params: {taskRunId: run.id},
                }).then(() => {
                    emit("update-logs", props.execution.id)
                })
            },
        )
    }

    async function fixErrorWithAi() {
        emit("dismiss")
        let taskRunLogs = currentTaskRun.value.id === props.taskRun.id ? props.attemptLogs ?? [] : []
        if (taskRunLogs.length === 0) {
            taskRunLogs = await executionsStore.loadLogs({
                store: false,
                executionId: props.execution.id,
                filters: [
                    {field: "taskRunId", operation: "EQUALS", value: currentTaskRun.value.id},
                    {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
                ],
                size: 100,
            }).catch((e: unknown) => {
                handledIf(e, [403, 404])
                return []
            })
        }
        const errorLines = (() => {
            const errors = taskRunLogs
                .filter((l) => (l.level || "")
                    .toString()
                    .toUpperCase() === "ERROR" && (l.message ?? "").length > 0)
            if (errors.length > 0) return errors.map((l) => l.message).join("\n")
            const last = [...taskRunLogs].reverse().find((l) => (l.message ?? "").length > 0)
            return last?.message ?? ""
        })()
        const prompt = `Fix the task ${currentTaskRun.value.taskId} as it generated the following error:\n${errorLines}`
        miscStore.promptCopilot(prompt, {title: t("ai.copilot.fixThread.task", {id: currentTaskRun.value.taskId}), newThread: true})
    }
</script>

<style scoped>
    .task-run-buttons {
        padding: 0 .5rem;
        border: none;
        background: transparent !important;

        &:not(:hover) {
            background: var(--ks-btn-secondary-bg-inactive);
        }
    }

    .fix-with-ai {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }
</style>
