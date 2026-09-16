<template>
    <KsDropdown trigger="click" :persistent="true">
        <KsButton type="default" class="task-run-buttons" :aria-label="$t('actions')">
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <li v-if="currentTaskRuns.length > 1" role="presentation" class="iteration-picker">
                    <KsSelect
                        v-model="selectedTaskRunId"
                        size="small"
                        :clearable="false"
                        :teleported="false"
                        :aria-label="$t('iteration')"
                    >
                        <KsOption
                            v-for="(run, index) in currentTaskRuns"
                            :key="run.id"
                            :value="run.id"
                            :label="run.value || `${$t('iteration')} ${run.iteration ?? (index + 1)}`"
                        />
                    </KsSelect>
                </li>
                <li v-if="currentTaskRuns.length > 1" class="el-dropdown-menu__item--divided m-0" role="separator"></li>
                <KsDropdownItem
                    v-if="selectedAttempt?.state.current === 'FAILED'"
                    @click="fixErrorWithAi"
                >
                    <span class="d-inline-flex align-items-center">
                        <AiIcon class="me-1" />
                        <span>{{ $t('fix_with_ai') }}</span>
                    </span>
                </KsDropdownItem>
                <SubFlowLink
                    v-if="isSubflow"
                    component="KsDropdownItem"
                    tabExecution="logs"
                    :executionId="currentTaskRun.outputs.executionId"
                />

                <Metrics :taskRun="currentTaskRun" :execution="execution" />

                <Outputs
                    :taskRun="currentTaskRun"
                    :executionId="execution.id"
                    :execution="execution"
                />

                <Restart
                    component="KsDropdownItem"
                    :key="`restart-${currentAttemptIndex}-${selectedAttempt?.state.startDate}-${currentTaskRun.id}`"
                    isReplay
                    tooltipPosition="left"
                    :execution="execution"
                    :taskRun="currentTaskRun"
                    :attemptIndex="currentAttemptIndex"
                    @follow="emit('follow', $event)"
                />

                <ChangeStatus
                    component="KsDropdownItem"
                    :key="`change-status-${currentAttemptIndex}-${selectedAttempt?.state.startDate}-${currentTaskRun.id}`"
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
                    @click="deleteLogs(currentTaskRun.id)"
                >
                    {{ $t("delete logs") }}
                </KsDropdownItem>
                <WorkerInfo
                    component="KsDropdownItem"
                    v-if="hasWorkerId !== null"
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
    import {useRoute} from "vue-router"

    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import Copy from "vue-material-design-icons/ContentCopy.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Download from "vue-material-design-icons/Download.vue"

    import {State} from "@kestra-io/design-system"

    import * as Utils from "../../utils/utils"
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
    const toast = useToast()
    const miscStore = useMiscStore()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const currentTaskRuns = computed(() => {
        return props.taskRuns?.filter((r: { id: string; taskId: string }) => r.taskId === props.taskRun?.taskId) || []
    })

    const selectedTaskRunId = ref(props.taskRun?.id)

    watch(() => currentTaskRuns.value.map((r: { id: string }) => r.id).join(), () => {
        if (currentTaskRuns.value.length > 0 && !currentTaskRuns.value.find((r: { id: string }) => r.id === selectedTaskRunId.value)) {
            selectedTaskRunId.value = currentTaskRuns.value[0].id
        }
    })

    const currentTaskRun = computed(() => {
        if (currentTaskRuns.value.length > 0) {
            return currentTaskRuns.value.find((r: { id: string; taskId: string }) => r.id === selectedTaskRunId.value) || props.taskRun
        }
        return props.taskRun
    })

    const currentAttemptIndex = computed(() => {
        if (currentTaskRun.value.attempts && props.attemptIndex < currentTaskRun.value.attempts.length) {
            return props.attemptIndex
        }
        return 0
    })

    function attempts(taskRun: any): any[] {
        if (props.execution.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    const selectedAttempt = computed(() => attempts(currentTaskRun.value)[currentAttemptIndex.value ?? 0])

    const isSubflow = computed<boolean>(() => currentTaskRun.value?.outputs?.executionId)

    const hasWorkerId = computed<boolean | null>(() =>
        currentTaskRun.value.attempts?.find((attempt: any) => attempt.workerId !== null) !== null,
    )

    const canReadFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.VIEW, route.params.namespace),
    )

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

    function deleteLogs(currentTaskRunId: string) {
        let msg = t("delete_log")
        if (currentTaskRuns.value.length > 1) {
            const iterationValue = currentTaskRun.value.value || `${t("iteration")} ${currentTaskRun.value.iteration ?? ((currentTaskRuns.value.findIndex(r => r.id === currentTaskRunId) ?? 0) + 1)}`
            msg = t("delete_log_iteration", {iteration: iterationValue})
        }

        toast.confirm(
            msg,
            async () => {
                await executionsStore.deleteLogs({
                    executionId: props.execution.id,
                    params: {taskRunId: currentTaskRunId},
                }).then((_: unknown) => {
                    emit("update-logs", props.execution.id)
                })
            },
        )
    }

    async function fixErrorWithAi() {
        let taskRunLogs = currentTaskRun.value.id === props.taskRun.id ? (props.attemptLogs ?? []) : []
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
    .iteration-picker {
        padding: var(--ks-spacing-2);
    }

    .task-run-buttons {
        padding: 0 .5rem;
        border: none;
        background: transparent !important;

        &:not(:hover) {
            background: var(--ks-btn-secondary-bg-inactive);
        }
    }

</style>
