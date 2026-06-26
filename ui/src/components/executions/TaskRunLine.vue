<template>
    <div
        v-if="!hideHeader"
        class="taskrun-header"
        :style="{'--depth': depth}"
    >
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
            <KsExecutionStatus
                size="small"
                :status="currentTaskRun.state.current"
                clickable
                :aria-label="t('filter by status', {status: currentTaskRun.state.current})"
                @click.stop="navigateToStateFilter(currentTaskRun.state.current)"
            />
        </div>

        <slot name="buttons" />

        <TaskRunActions
            :taskRun="currentTaskRun"
            :execution="followedExecution"
            :flow="flow"
            :attemptIndex="selectedAttemptNumberByTaskRunId[currentTaskRun.id] ?? 0"
            :forcedAttemptNumber="forcedAttemptNumber"
            :attemptLogs="logsWithIndexByAttemptUid[attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])] ?? []"
            @follow="emit('follow', $event)"
            @update-logs="emit('update-logs', $event)"
        />
    </div>
    <div class="attempt-header" :class="{'attempt-header--flush': hideHeader}">
        <KsSelect
            class="d-none d-md-inline-block attempt-select"
            :modelValue="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
            @change="emit('swapDisplayedAttempt', {taskRunId: currentTaskRun.id, attemptNumber: $event})"
            :disabled="!currentTaskRun.attempts || currentTaskRun.attempts?.length <= 1"
        >
            <template #label="{value}">
                {{ `${t('attempt')} ${(value ?? 0) + 1}/${attempts(currentTaskRun).length}` }}
            </template>
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
    import {State, KsExecutionStatus, KsTaskIcon} from "@kestra-io/design-system"
    import TaskRunActions from "./TaskRunActions.vue"
    import {useStateFilter} from "../filter/composables/useStateFilter"
    import Clock from "vue-material-design-icons/Clock.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import * as FlowUtils from "../../utils/flowUtils"
    import _groupBy from "lodash/groupBy"
    import {Duration} from "@kestra-io/topology"
    import {usePluginsStore} from "../../stores/plugins"
    import {date as dateFilter, humanizeDuration} from "../../utils/filters"

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()
    const {navigateToStateFilter} = useStateFilter()

    interface Props {
        currentTaskRun: any
        followedExecution: any
        flow?: any
        forcedAttemptNumber?: number
        taskRunId?: string
        selectedAttemptNumberByTaskRunId?: Record<string, number>
        shownAttemptsUid?: string[]
        logs?: any[]
        filter?: string
        hideHeader?: boolean
        depth?: number
    }

    const props = withDefaults(defineProps<Props>(), {
        flow: undefined,
        forcedAttemptNumber: undefined,
        taskRunId: undefined,
        selectedAttemptNumberByTaskRunId: () => ({}),
        shownAttemptsUid: () => [],
        logs: () => [],
        filter: "",
        hideHeader: false,
        depth: 0,
    })

    const emit = defineEmits<{
        toggleShowAttempt: [uid: string]
        swapDisplayedAttempt: [event: {taskRunId: string; attemptNumber: number}]
        follow: [event: unknown]
        "update-logs": [executionId: string]
    }>()

    // computed
    const currentTaskRuns = computed(() =>
        props.followedExecution?.taskRunList?.filter((tr: any) => props.taskRunId ? tr.id === props.taskRunId : true) ?? [],
    )

    const taskRunById = computed(() =>
        Object.fromEntries(currentTaskRuns.value.map((taskRun: any) => [taskRun.id, taskRun])),
    )

    const logsWithIndexByAttemptUid = computed(() => {
        let indexedLogs = props.logs
            .filter((logLine: any) =>
                (logLine?.message ?? "").toLowerCase().includes(props.filter) || isSubflow(taskRunById.value[logLine.taskRunId]),
            )
            .map((logLine: any, index: number) => ({...logLine, index}))

        // Remove duplicate logs based on taskRunId and attemptNumber, keeping the one with the highest index (most recent)
        indexedLogs = Array.from(new Set(indexedLogs))

        return _groupBy(indexedLogs, (indexedLog: any) => attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber))
    })

    // methods
    function attempts(taskRun: any): any[] {
        if (props.followedExecution.state.current === State.RUNNING || props.forcedAttemptNumber === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[props.forcedAttemptNumber]] : []
    }

    function isSubflow(taskRun: any): boolean {
        return taskRun?.outputs?.executionId
    }

    function selectedAttempt(taskRun: any): any {
        return attempts(taskRun)[props.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0]
    }

    function taskType(taskRun: any): string | undefined {
        if (!taskRun) return undefined
        const task = FlowUtils.findTaskById(props.flow, taskRun.taskId)
        const parentTaskRunId = taskRun.parentTaskRunId
        if (task === undefined && parentTaskRunId) {
            return taskType(taskRunById.value[parentTaskRunId])
        }
        return task ? (task as any).type : undefined
    }

    function attemptUid(taskRunId: string, attemptNumber: number): string {
        return `${taskRunId}-${attemptNumber}`
    }

    function shouldDisplayChevron(taskRun: any): boolean {
        return shouldDisplayLogs(taskRun.id)
    }

    function shouldDisplayLogs(taskRunId: string): boolean {
        return !!(logsWithIndexByAttemptUid.value[attemptUid(taskRunId, props.selectedAttemptNumberByTaskRunId[taskRunId])])
    }
</script>

<style scoped lang="scss">
    .task-duration {
        padding: .375rem 0;
    }

    .taskrun-header,
    .attempt-header {
        display: flex;
        gap: .5rem;
        padding: 0.5rem 1rem;
        border-bottom: 1px solid var(--ks-border-default);

        >* {
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
        padding-left: calc(var(--ks-spacing-4) + var(--depth, 0) * var(--ks-spacing-5));

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
            }
        }
    }

    .attempt-header.attempt-header--flush {
        border-bottom: none;
        padding: 0;
        padding-bottom: 1rem;
    }

    .attempt-header {
        .kel-select {
            width: 115px;
            height: 32px;

            :deep(.kel-select__wrapper) {
                height: 32px;
                min-height: 32px;
            }

        }

        .attempt-number {
            background: var(--ks-bg-tag);
            padding: .375rem .75rem;
            white-space: nowrap;
        }
    }
</style>
