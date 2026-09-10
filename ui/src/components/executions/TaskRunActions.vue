<template>
    <KsDropdown trigger="click" :persistent="true">
        <KsButton type="default" class="task-run-buttons" :aria-label="$t('actions')">
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <div v-if="taskRuns && taskRuns.length > 1" class="px-3 py-2 border-bottom">
                    <KsSelect
                        v-model="selectedTaskRunIndex"
                        size="small"
                        :clearable="false"
                    >
                        <template #label="{value}">
                            {{ $t('iteration') }} {{ value + 1 }}
                        </template>
                        <KsOption
                            v-for="(run, index) in taskRuns"
                            :key="run.id"
                            :value="index"
                            :label="run.value || `${$t('iteration')} ${index + 1}`"
                        />
                    </KsSelect>
                </div>
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
                    :key="`restart-${attemptIndex}-${selectedAttempt?.state.startDate}`"
                    isReplay
                    tooltipPosition="left"
                    :execution="execution"
                    :taskRun="currentTaskRun"
                    :attemptIndex="attemptIndex"
                    @follow="emit('follow', $event)"
                />

                <ChangeStatus
                    component="KsDropdownItem"
                    :key="`change-status-${attemptIndex}-${selectedAttempt?.state.startDate}`"
                    :execution="execution"
                    :taskRun="currentTaskRun"
                    :attemptIndex="attemptIndex"
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

    const selectedTaskRunIndex = ref(0)
    watch(() => props.taskRuns, (newRuns) => {
        if (!newRuns || newRuns.length === 0) {
            selectedTaskRunIndex.value = 0
        } else if (selectedTaskRunIndex.value >= newRuns.length) {
            selectedTaskRunIndex.value = newRuns.length - 1
        }
    }, {deep: true})
    
    const currentTaskRun = computed(() => {
        if (props.taskRuns && props.taskRuns.length > 0) {
            return props.taskRuns[selectedTaskRunIndex.value] || props.taskRun
        }
        return props.taskRun
    })

    function attempts(taskRun: any): any[] {
        if (props.execution.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    const selectedAttempt = computed(() => attempts(currentTaskRun.value)[props.attemptIndex ?? 0])

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
        toast.confirm(
            t("delete_log"),
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
        let taskRunLogs = props.attemptLogs ?? []
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

</style>
