<template>
    <div class="taskrun-header">
        <div>
            <KsIcon
                v-if="!taskRunId && shouldDisplayChevron(currentTaskRun)"
                type="default"
                @click.stop="() => emit('toggleShowAttempt', (attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])))"
            >
                <ChevronDown
                    v-if="shownAttemptsUid.includes(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                />
                <ChevronRight v-else />
            </KsIcon>
        </div>
        <div class="task-icon d-none d-md-inline-block me-1">
            <KsTaskIcon
                :cls="taskType(currentTaskRun)"
                v-if="taskType(currentTaskRun)"
                onlyIcon
                :icons="pluginsStore.icons"
            />
        </div>

        <div
            class="task-id flex-grow-1"
            :id="`attempt-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${currentTaskRun.id}`"
        >
            <KsTooltip>
                <template #content>
                    {{ t("from") }} :
                    {{ dateFilter(selectedAttempt(currentTaskRun).state.startDate) }}
                    <br>
                    {{ t("to") }} :
                    {{ dateFilter(selectedAttempt(currentTaskRun).state.endDate) }}
                    <br>
                    <Clock />
                    <strong>{{ t("duration") }}:</strong>
                    {{ humanizeDuration(selectedAttempt(currentTaskRun).state.duration) }}
                </template>
                <span>
                    <span class="me-1 fw-bold">{{ currentTaskRun.taskId }}</span>
                    <small v-if="currentTaskRun.value">
                        {{ currentTaskRun.value }}
                    </small>
                </span>
            </KsTooltip>
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <Duration :histories="currentTaskRun.state.histories" />
            </small>
        </div>

        <div class="task-status">
            <KsExecutionStatus size="small" :status="currentTaskRun.state.current" />
        </div>

        <slot name="buttons" />

        <KsDropdown trigger="click" :persistent="true">
            <KsButton type="default" class="task-run-buttons" :aria-label="$t('actions')">
                <DotsVertical />
            </KsButton>
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem
                        v-if="selectedAttempt(currentTaskRun).state.current === 'FAILED'"
                        @click="fixErrorWithAi(currentTaskRun)"
                    >
                        <span class="d-inline-flex align-items-center">
                            <AiIcon class="me-1" />
                            <span>{{ t('fix_with_ai') }}</span>
                        </span>
                    </KsDropdownItem>
                    <SubFlowLink
                        v-if="isSubflow(currentTaskRun)"
                        component="el-dropdown-item"
                        tabExecution="logs"
                        :executionId="currentTaskRun.outputs.executionId"
                    />

                    <Metrics :taskRun="currentTaskRun" :execution="followedExecution" />

                    <Outputs
                        :outputs="currentTaskRun.outputs"
                        :execution="followedExecution"
                    />

                    <Restart
                        component="el-dropdown-item"
                        :key="`restart-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        isReplay
                        tooltipPosition="left"
                        :execution="followedExecution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="emit('follow', $event)"
                    />

                    <ChangeStatus
                        component="el-dropdown-item"
                        :key="`change-status-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        :execution="followedExecution"
                        :taskRun="currentTaskRun"
                        :attemptIndex="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="emit('follow', $event)"
                    />
                    <TaskEdit
                        v-if="canReadFlow"
                        :readOnly="true"
                        component="el-dropdown-item"
                        :taskId="currentTaskRun.taskId"
                        section="tasks"
                        :flowId="followedExecution.flowId"
                        :namespace="followedExecution.namespace"
                        :revision="followedExecution.flowRevision"
                        :flowSource="flow?.source"
                    />
                    <KsDropdownItem
                        :icon="Download"
                        @click="downloadContent(currentTaskRun.id)"
                    >
                        {{ t("download logs") }}
                    </KsDropdownItem>
                    <KsDropdownItem
                        :icon="Copy"
                        @click="copyContent(currentTaskRun.id)"
                    >
                        {{ t("copy logs") }}
                    </KsDropdownItem>
                    <KsDropdownItem
                        :icon="Delete"
                        @click="deleteLogs(currentTaskRun.id)"
                    >
                        {{ t("delete logs") }}
                    </KsDropdownItem>
                    <WorkerInfo
                        component="el-dropdown-item"
                        v-if="hasWorkerId(currentTaskRun) !== null"
                        :taskRun="currentTaskRun"
                        @follow="emit('follow', $event)"
                    />
                </KsDropdownMenu>
            </template>
        </KsDropdown>
    </div>
    <div class="attempt-header">
        <KsSelect
            class="d-none d-md-inline-block attempt-select"
            :modelValue="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
            @change="emit('swapDisplayedAttempt', {taskRunId: currentTaskRun.id, attemptNumber: $event})"
            :disabled="!currentTaskRun.attempts || currentTaskRun.attempts?.length <= 1"
        >
            <KsOption
                v-for="(_, index) in attempts(currentTaskRun)"
                :key="`attempt-${index}-${currentTaskRun.id}`"
                :value="index"
                :label="`${t('attempt')} ${index + 1}`"
            />
        </KsSelect>

        <div class="task-status">
            <KsExecutionStatus size="small" :status="selectedAttempt(currentTaskRun).state.current" />
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <Duration :histories="selectedAttempt(currentTaskRun).state.histories" />
            </small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import Restart from "./overview/components/actions/Restart.vue"
    import Metrics from "./Metrics.vue"
    import {State} from "@kestra-io/design-system"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import ChangeStatus from "./ChangeStatus.vue"
    import TaskEdit from "../flows/TaskEdit.vue"
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import Outputs from "./Outputs.vue"
    import Clock from "vue-material-design-icons/Clock.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import Copy from "vue-material-design-icons/ContentCopy.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import WorkerInfo from "./WorkerInfo.vue"
    import AiIcon from "../ai/AiIcon.vue"
    import * as FlowUtils from "../../utils/flowUtils"
    import _groupBy from "lodash/groupBy"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import Duration from "../layout/Duration.vue"
    import * as Utils from "../../utils/utils"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {usePluginsStore} from "../../stores/plugins"
    import {useCoreStore} from "../../stores/core"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import {date as dateFilter, humanizeDuration} from "../../utils/filters"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()
    const pluginsStore = usePluginsStore()
    const coreStore = useCoreStore()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    interface Props {
        currentTaskRun: any // FIXME: any
        followedExecution: any // FIXME: any
        flow?: any // FIXME: any
        forcedAttemptNumber?: number
        taskRunId?: string
        selectedAttemptNumberByTaskRunId?: Record<string, number>
        shownAttemptsUid?: string[]
        logs?: any[] // FIXME: any
        filter?: string
    }

    const props = withDefaults(defineProps<Props>(), {
        flow: undefined,
        forcedAttemptNumber: undefined,
        taskRunId: undefined,
        selectedAttemptNumberByTaskRunId: () => ({}),
        shownAttemptsUid: () => [],
        logs: () => [],
        filter: "",
    })

    const emit = defineEmits<{
        toggleShowAttempt: [uid: string]
        swapDisplayedAttempt: [event: {taskRunId: string; attemptNumber: number}]
        follow: [event: unknown]
        "update-logs": [executionId: string]
    }>()

    // computed
    const currentTaskRuns = computed(() =>
        props.followedExecution?.taskRunList?.filter((tr: any) => props.taskRunId ? tr.id === props.taskRunId : true) ?? [], // FIXME: any
    )

    const taskRunById = computed(() =>
        Object.fromEntries(currentTaskRuns.value.map((taskRun: any) => [taskRun.id, taskRun])), // FIXME: any
    )

    const logsWithIndexByAttemptUid = computed(() => {
        let indexedLogs = props.logs
            .filter((logLine: any) => // FIXME: any
                (logLine?.message ?? "").toLowerCase().includes(props.filter) || isSubflow(taskRunById.value[logLine.taskRunId]),
            )
            .map((logLine: any, index: number) => ({...logLine, index})) // FIXME: any

        // Remove duplicate logs based on taskRunId and attemptNumber, keeping the one with the highest index (most recent)
        indexedLogs = Array.from(new Set(indexedLogs))

        return _groupBy(indexedLogs, (indexedLog: any) => attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber)) // FIXME: any
    })

    const canReadFlow = computed(() =>
        authStore.user?.isAllowed(resource.FLOW, action.VIEW, route.params.namespace),
    )

    // methods
    function attempts(taskRun: any): any[] { // FIXME: any
        if (props.followedExecution.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    function isSubflow(taskRun: any): boolean { // FIXME: any
        return taskRun?.outputs?.executionId
    }

    function downloadNameFor(currentTaskRunId: string): string {
        const now = new Date()
        const pad = (n: number) => String(n).padStart(2, "0")
        const formatted = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
        return `kestra-execution-${formatted}-${props.followedExecution.id}-${currentTaskRunId}.log`
    }

    function selectedAttempt(taskRun: any): any { // FIXME: any
        return attempts(taskRun)[props.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0]
    }

    function taskType(taskRun: any): string | undefined { // FIXME: any
        if (!taskRun) return undefined
        const task = FlowUtils.findTaskById(props.flow, taskRun.taskId)
        const parentTaskRunId = taskRun.parentTaskRunId
        if (task === undefined && parentTaskRunId) {
            return taskType(taskRunById.value[parentTaskRunId])
        }
        return task ? (task as any).type : undefined // FIXME: any
    }

    function downloadContent(currentTaskRunId: string) {
        const params: Record<string, unknown> = {}
        executionsStore.downloadLogs({
            executionId: props.followedExecution.id,
            params: {...params, taskRunId: currentTaskRunId},
        }).then((response: unknown) => {
            Utils.downloadUrl(window.URL.createObjectURL(new Blob([response as BlobPart])), downloadNameFor(currentTaskRunId))
        })
    }

    function copyContent(currentTaskRunId: string) {
        const params: Record<string, unknown> = {}
        executionsStore.downloadLogs({
            executionId: props.followedExecution.id,
            params: {...params, taskRunId: currentTaskRunId},
        }).then((response: unknown) => {
            Utils.copy(response as string).then(() => {
                coreStore.message = {
                    variant: "success",
                    title: t("success"),
                    message: t("copied_logs_to_clipboard"),
                }
            })
        })
    }

    function deleteLogs(currentTaskRunId: string) {
        const params: Record<string, unknown> = {}
        toast.confirm(
            t("delete_log"),
            async () => {
                await executionsStore.deleteLogs({
                    executionId: props.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId},
                }).then((_: unknown) => {
                    emit("update-logs", props.followedExecution.id)
                })
            },
        )
    }

    function hasWorkerId(currentTaskRun: any): boolean | null { // FIXME: any
        return currentTaskRun.attempts?.find((attempt: any) => attempt.workerId !== null) !== null // FIXME: any
    }

    function attemptUid(taskRunId: string, attemptNumber: number): string {
        return `${taskRunId}-${attemptNumber}`
    }

    function shouldDisplayChevron(taskRun: any): boolean { // FIXME: any
        return shouldDisplayLogs(taskRun.id)
    }

    function shouldDisplayLogs(taskRunId: string): boolean {
        return !!(logsWithIndexByAttemptUid.value[attemptUid(taskRunId, props.selectedAttemptNumberByTaskRunId[taskRunId])])
    }

    function fixErrorWithAi(taskRun: any) { // FIXME: any
        const attemptNumber = props.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0
        const uid = attemptUid(taskRun.id, attemptNumber)
        const taskRunLogs = logsWithIndexByAttemptUid.value[uid] ?? []
        const errorLines = (() => {
            const errors = taskRunLogs.filter((l: any) => (l.level || "").toString().toUpperCase() === "ERROR" && (l.message ?? "").length > 0) // FIXME: any
            if (errors.length > 0) return errors.map((l: any) => l.message).join("\n") // FIXME: any
            const last = [...taskRunLogs].reverse().find((l: any) => (l.message ?? "").length > 0) // FIXME: any
            return last?.message ?? ""
        })()
        const prompt = `Fix the task ${taskRun.taskId} as it generated the following error:\n${errorLines}`
        try {
            window.sessionStorage.setItem("kestra-ai-prompt", prompt)
        } catch (err) {
            console.warn("AI prompt not persisted to sessionStorage:", err)
        }

        router.push({
            name: "flows/update",
            params: {
                namespace: props.followedExecution.namespace,
                id: props.followedExecution.flowId,
                tab: "edit",
                tenant: route.params?.tenant,
            },
            query: {ai: "open"},
        })
    }
</script>

<style scoped lang="scss">

  .task-duration {
    padding: .375rem 0;
  }

  .taskrun-header, .attempt-header {
    display: flex;
    gap: .5rem;
    padding: 0.5rem 1rem;
    border-bottom: 1px solid var(--ks-border-default);

    > * {
      display: flex;
      align-items: center;
    }

    small {
      font-family: var(--kel-font-family-monospace);
      font-size: var(--ks-font-size-xs)
    }

    .task-duration small {
      white-space: nowrap;
      color: var(--ks-text-secondary);
    }

  }

  .taskrun-header {
    background-color: var(--ks-bg-surface);
    .task-icon {
      width: 36px;
      padding: 6px 6px 6px 0;
      border-radius: 0.5rem;
      margin-left: -0.5rem;
    }

    .task-id {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      padding: .375rem 0;

      span span {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);

        html:not(.dark) & {
          color: #26282D;
        }
      }
    }

    .task-run-buttons {
      padding: 0 .5rem;
      border: 1px solid rgba(#FFFFFF, .05);
      background-color: var(--ks-btn-secondary-bg-default) !important;
      // FIXME: what does this mean?
      &:not(:hover) {
        background: rgba(#FFFFFF, .10);
      }
    }
  }

  .attempt-header {
    .kel-select {
      width: 10rem;
      height: 24px;
      margin-top: 0.35rem;

      :deep(.kel-select__wrapper) {
        height: 24px;
        min-height: 24px;
      }

    }

    .attempt-number {
      background: var(--ks-bg-tag);
      padding: .375rem .75rem;
      white-space: nowrap;
    }
  }

  :deep(.attempt-select > .kel-select__wrapper) {
    height: 100%;
  }
</style>
