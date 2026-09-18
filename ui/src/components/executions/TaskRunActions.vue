<template>
    <KsDropdown trigger="click" :persistent="true">
        <KsButton type="default" class="task-run-buttons" :aria-label="$t('actions')">
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <li role="presentation" v-if="currentTaskRuns.length > 1" class="iteration-selector">
                    <KsSelect
                        v-model="selectedTaskRunId"
                        :teleported="false"
                        :aria-label="$t('iteration')"
                    >
                        <KsOption
                            v-for="(run, index) in currentTaskRuns"
                            :key="run.id"
                            :value="run.id"
                            :label="run.value !== undefined && run.value !== null ? run.value : $t('iteration_number', {number: index + 1})"
                        />
                    </KsSelect>
                </li>

                <KsDropdownItem
                    v-if="selectedAttempt?.state.current === 'FAILED'"
                    :divided="currentTaskRuns.length > 1"
                    @click="fixErrorWithAi"
                >
                    <span class="d-inline-flex align-items-center">
                        <AiIcon class="me-1" />
                        <span>{{ $t('fix_with_ai') }}</span>
                    </span>
                </KsDropdownItem>
                <SubFlowLink
                    v-if="isSubflow"
                    :divided="currentTaskRuns.length > 1 && selectedAttempt?.state.current !== 'FAILED'"
                    component="KsDropdownItem"
                    tabExecution="logs"
                    :executionId="currentTaskRun.outputs?.executionId"
                />
                <KsDropdownItem
                    v-if="isLoop"
                    :icon="Repeat"
                    @click="openIterations"
                >
                    {{ $t("iterations") }}
                </KsDropdownItem>

                <Metrics
                    :divided="currentTaskRuns.length > 1 && selectedAttempt?.state.current !== 'FAILED' && !isSubflow"
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
                    :taskRun="currentTaskRun"
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
                <KsDropdownItem
                    :icon="Download"
                    @click="downloadContent(currentTaskRun.id)"
                >
                    {{ $t("download logs") }}
                </KsDropdownItem>
                <KsDropdownItem
                    :icon="Copy"
                    @click="copyContent(currentTaskRun.id)"
                >
                    {{ $t("copy logs") }}
                </KsDropdownItem>
                <KsDropdownItem
                    :icon="Delete"
                    @click="deleteLogs(currentTaskRun)"
                >
                    {{ $t("delete logs") }}
                </KsDropdownItem>
                <WorkerInfo
                    component="KsDropdownItem"
                    v-if="hasWorkerId"
                    :taskRun="currentTaskRun"
                    @follow="emit('follow', $event)"
                />

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
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import Copy from "vue-material-design-icons/ContentCopy.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import Repeat from "vue-material-design-icons/Repeat.vue"

    import {State} from "@kestra-io/design-system"

    import * as Utils from "../../utils/utils"
    import {findTaskById} from "../../utils/flowUtils"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useCoreStore} from "../../stores/core"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import Restart from "./overview/components/actions/Restart.vue"
    import Metrics from "./Metrics.vue"
    import ChangeStatus from "./ChangeStatus.vue"
    import Outputs from "./Outputs.vue"
    import WorkerInfo from "./WorkerInfo.vue"
    import TaskEdit from "../flows/TaskEdit.vue"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import AiIcon from "../ai/AiIcon.vue"
    import {NodeMenuItem, type NodeAction} from "@kestra-io/topology"

    const props = withDefaults(defineProps<{
        taskRun: any
        taskRuns?: any[]
        execution: any
        flow?: any
        taskType?: string
        attemptIndex?: number
        forcedAttemptNumber?: number
        attemptLogs?: any[]
        nodeActions?: NodeAction[]
    }>(), {
        flow: undefined,
        attemptIndex: 0,
        forcedAttemptNumber: undefined,
        attemptLogs: () => [],
        nodeActions: () => [],
    })

    const emit = defineEmits<{
        follow: [event: unknown]
        "update-logs": [executionId: string]
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const miscStore = useMiscStore()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const currentTaskRuns = computed(() => {
        return (props.taskRuns || []).filter((r) => r.taskId === props.taskRun?.taskId)
    })

    const selectedTaskRunId = ref<string>(props.taskRun?.id || currentTaskRuns.value[0]?.id)

    const currentTaskRun = computed(() => {
        return currentTaskRuns.value.find((r) => r.id === selectedTaskRunId.value) || props.taskRun
    })

    watch(() => currentTaskRuns.value.map((r) => r.id).join(), () => {
        if (!currentTaskRuns.value.some((r) => r.id === selectedTaskRunId.value)) {
            selectedTaskRunId.value = props.taskRun?.id || currentTaskRuns.value[0]?.id
        }
    })

    function attempts(taskRun: any): any[] {
        if (props.execution.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    const currentAttemptIndex = computed(() => {
        const attemptCount = attempts(currentTaskRun.value).length
        return Math.min(props.attemptIndex ?? 0, Math.max(0, attemptCount - 1))
    })

    const selectedAttempt = computed(() => attempts(currentTaskRun.value)[currentAttemptIndex.value])

    const isSubflow = computed<boolean>(() => !!currentTaskRun.value?.outputs?.executionId)
    const isSubflow = computed<boolean>(() => props.taskRun?.outputs?.executionId)
    const isLoop = computed(() => (props.taskType ?? findTaskById(props.flow, props.taskRun.taskId)?.type) === "io.kestra.plugin.core.flow.Loop")

    const hasWorkerId = computed<boolean>(() =>
        currentTaskRun.value.attempts?.find((attempt: { workerId?: string | null }) => attempt.workerId !== null) !== undefined,
    )

    const canReadFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.VIEW, String(route.params.namespace)),
    )

    function openIterations() {
        router.push({
            name: "executions/list",
            query: {
                "filters[parentId][EQUALS]": props.execution.id,
                "filters[kind][EQUALS]": "LOOP",
                "filters[taskId][EQUALS]": props.taskRun.taskId,
            },
        })
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
            params: {taskRunId: currentTaskRunId},
        }).then((response: unknown) => {
            Utils.downloadUrl(window.URL.createObjectURL(new Blob([response as BlobPart])), downloadNameFor(currentTaskRunId))
        })
    }

    function copyContent(currentTaskRunId: string) {
        executionsStore.downloadLogs({
            executionId: props.execution.id,
            params: {taskRunId: currentTaskRunId},
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

    function deleteLogs(run: any) {
        const iterationLabel = run.value !== undefined && run.value !== null ? run.value : t("iteration_number", {number: currentTaskRuns.value.findIndex(r => r.id === run.id) + 1})
        
        toast.confirm(
            currentTaskRuns.value.length > 1 ? t("delete_log_iteration", {iteration: iterationLabel}) : t("delete_log"),
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
        let taskRunLogs = currentTaskRun.value.id === props.taskRun.id ? props.attemptLogs ?? [] : []
        if (taskRunLogs.length === 0) {
            taskRunLogs = await executionsStore.loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {taskRunId: currentTaskRun.value.id, minLevel: "ERROR"},
                showMessageOnError: false,
            }).catch(() => [])
        }
        const errorLines = (() => {
            const errors = taskRunLogs
                .filter((l: any) => (l.level || "")
                    .toString()
                    .toUpperCase() === "ERROR" && (l.message ?? "").length > 0)
            if (errors.length > 0) return errors.map((l: any) => l.message).join("\n")
            const last = [...taskRunLogs].reverse().find((l: any) => (l.message ?? "").length > 0)
            return last?.message ?? ""
        })()
        const prompt = `Fix the task ${currentTaskRun.value.taskId} as it generated the following error:\n${errorLines}`
        miscStore.promptCopilot(prompt, {title: t("ai.copilot.fixThread.task", {id: currentTaskRun.value.taskId}), newThread: true})
    }
</script>

<style scoped lang="scss">
    .task-run-buttons {
        padding: 0 .5rem;
        border: none;
        background: transparent !important;

        &:not(:hover) {
            background: var(--ks-btn-secondary-bg-inactive);
        }
    }
    
    .iteration-selector {
        padding: var(--ks-spacing-2);
    }
</style>
