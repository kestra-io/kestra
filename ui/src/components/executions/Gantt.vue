<template>
    <ExecutionPending
        v-if="isQueued"
        :execution="execution!"
    />
    <template v-else-if="execution && executionsStore.flow">
        <ExecutionProgress
            v-if="isProgressing"
            :execution="execution"
            class="gantt-progress"
        />
        <!-- No task runs to plot: hide the filter bar + card and show only the execution
             status (mirrors the versioned-plugins empty screen). -->
        <KsEmptyState v-if="series.length === 0" :image="emptyIllustration">
            <template #description>
                <span class="gantt-empty-status">
                    {{ $t("execution_status") }}
                    <KsExecutionStatus :status="execution.state.current" />
                </span>
                <span v-if="emptyStateHint" class="gantt-empty-hint">{{ emptyStateHint }}</span>
            </template>
        </KsEmptyState>
        <template v-else>
            <KSFilter
                :configuration="ganttExecutionFilter"
                :tableOptions="{
                    chart: {shown: false},
                    columns: {shown: false},
                    refresh: {shown: true, callback: compute}
                }"
                @search="search = $event"
                @filter="onFilterChange"
            />
            <div class="gantt-stage">
                <KsCard
                    id="gantt"
                    data-onboarding-target="execution-gantt"
                    shadow="never"
                    :class="{'no-border': !hasValidDate, 'is-compact': verticalLayout}"
                >
                    <template #header v-if="hasValidDate">
                        <div class="gantt-header">
                            <div class="top">
                                <div class="summary">
                                    <span class="item">
                                        <span class="label">{{ $t("total_duration") }}</span>
                                        <Duration class="value" :histories="execution.state.histories" />
                                    </span>
                                    <span class="separator">/</span>
                                    <span class="item">
                                        <span class="label">{{ $t("tasks") }}</span>
                                        <span class="value">{{ tasksSummary }}</span>
                                    </span>
                                </div>
                                <div class="actions">
                                    <KsButton class="copy-logs" :icon="ContentCopy" link :aria-label="$t('copy all logs')" @click="copyAllLogs">
                                        <span class="copy-logs-label">{{ $t("copy all logs") }}</span>
                                    </KsButton>
                                    <KsExecutionStatus :status="execution.state.current" />
                                </div>
                            </div>
                            <div class="bottom">
                                <div class="scale" data-test="gantt-scale">
                                    <span>{{ formatDuration(0) }}</span>
                                    <span>{{ formatDuration(delta()) }}</span>
                                </div>
                            </div>
                        </div>
                    </template>
                    <template #default>
                        <DynamicScroller
                            v-if="filteredSeries.length > 0"
                            :items="filteredSeries"
                            :minItemSize="40"
                            keyField="id"
                            :buffer="0"
                            :updateInterval="0"
                        >
                            <template #default="{item, index, active}">
                                <DynamicScrollerItem
                                    :item="item"
                                    :active="active"
                                    :data-index="index"
                                >
                                    <div class="task-wrapper">
                                        <div
                                            class="gantt-row cursor-icon"
                                            :class="{
                                                'is-expanded': selectedTaskRuns.includes(item.id),
                                                'gantt-block-alt': rowBlocks.get(item.id)?.isAlt,
                                                'gantt-block-start': rowBlocks.get(item.id)?.isStart,
                                            }"
                                            @click="onTaskSelect(item.id)"
                                        >
                                            <div
                                                v-if="!verticalLayout"
                                                class="gantt-rails"
                                                aria-hidden="true"
                                            >
                                                <span
                                                    v-if="hasChildren(item)"
                                                    class="gantt-rail gantt-rail-outgoing"
                                                    :style="{'--rail-depth': item.depth || 0}"
                                                />
                                                <span
                                                    v-for="depth in continuingRailDepths(item)"
                                                    :key="depth"
                                                    class="gantt-rail"
                                                    :style="{'--rail-depth': depth}"
                                                />
                                                <span
                                                    v-if="(item.depth ?? 0) > 0"
                                                    class="gantt-rail gantt-rail-elbow"
                                                    :style="{'--rail-depth': (item.depth ?? 1) - 1}"
                                                />
                                            </div>
                                            <div
                                                v-if="!verticalLayout"
                                                class="gantt-chevron"
                                                :style="{'--depth': item.depth || 0}"
                                            >
                                                <ChevronRight v-if="!selectedTaskRuns.includes(item.id)" />
                                                <ChevronDown v-else />
                                            </div>
                                            <div
                                                class="task-label"
                                                data-test="gantt-task-label"
                                                :style="{'--depth': item.depth || 0}"
                                            >
                                                <div v-if="taskTypeByTaskRunId[item.id]" class="task-icon-box">
                                                    <TaskIcon :cls="taskTypeByTaskRunId[item.id]" onlyIcon :loadIcon="pluginsStore.loadIcon" />
                                                </div>
                                                <KsTooltip placement="top-start">
                                                    <template #content>
                                                        <code>{{ item.name }}</code>
                                                        <small v-if="item.task?.value"><br>{{ item.task.value }}</small>
                                                    </template>
                                                    <span class="task-name">
                                                        <code :title="verticalLayout ? item.name : undefined">{{ item.name }}</code>
                                                        <small v-if="item.task?.value"> {{ item.task.value }}</small>
                                                    </span>
                                                </KsTooltip>
                                            </div>
                                            <div class="task-attempts">
                                                <KsTooltip v-if="item.attempts > 1" placement="right">
                                                    <template #content>
                                                        <span>{{ $t("this_task_has") }} {{ item.attempts }} {{ $t("attempts").toLowerCase() }}.</span>
                                                    </template>
                                                    <Warning class="attempt_warn" />
                                                </KsTooltip>
                                            </div>
                                            <div v-if="!verticalLayout" class="task-offset" data-test="gantt-task-offset">
                                                {{ item.offset }}
                                            </div>
                                            <div class="task-bar">
                                                <div
                                                    :style="taskBarStyle(item)"
                                                    class="task-progress"
                                                    data-test="gantt-task-bar"
                                                />
                                            </div>
                                            <div class="task-duration">
                                                <small>
                                                    <Duration :histories="item.task.state.histories" :attemptCount="item.attempts" :subject="item.name" />
                                                </small>
                                            </div>
                                            <div class="task-state">
                                                <KsExecutionStatus :status="item.task.state.current" />
                                            </div>
                                            <div class="task-actions" @click.stop>
                                                <TaskRunActions
                                                    :taskRun="item.task"
                                                    :taskType="taskTypeByTaskRunId[item.task.id]"
                                                    :execution="execution"
                                                    :flow="executionsStore.flow"
                                                />
                                            </div>
                                            <div
                                                v-if="verticalLayout && item.id === firstFailure.failedTaskRun.value?.id"
                                                class="task-failure"
                                                data-test="gantt-first-failure"
                                                @click.stop
                                            >
                                                <p v-if="firstFailure.message.value" class="task-failure-message">
                                                    {{ firstFailure.message.value }}
                                                </p>
                                                <RouterLink class="task-failure-link" :to="firstFailure.logsRoute.value">
                                                    {{ $t("execution_mobile.open_logs") }}
                                                    <ArrowRight />
                                                </RouterLink>
                                            </div>
                                        </div>
                                        <LoopIterationTree
                                            v-if="taskTypeByTaskRunId[item.id] === 'io.kestra.plugin.core.flow.Loop'"
                                            :key="`${execution!.id}-${item.task.taskId}-${item.id}`"
                                            :executionId="execution!.id"
                                            :taskId="item.task.taskId"
                                            :namespace="execution!.namespace"
                                            :flowId="execution!.flowId"
                                            :depth="item.depth || 0"
                                        />
                                        <Transition name="expand">
                                            <div v-if="selectedTaskRuns.includes(item.id)" class="task-details">
                                                <div class="task-details__inner">
                                                    <GanttTaskLogs
                                                        :taskRun="item.task"
                                                        :taskType="taskTypeByTaskRunId[item.id]"
                                                        :execution="execution"
                                                        :flow="executionsStore.flow"
                                                        :levelFilter="effectiveSelectedLogLevel"
                                                        class="task-logs"
                                                    />
                                                </div>
                                            </div>
                                        </Transition>
                                    </div>
                                </DynamicScrollerItem>
                            </template>
                        </DynamicScroller>
                        <!-- Task runs exist but the active filters/search hid them all. -->
                        <KsNoData
                            v-else
                            :title="$t('gantt_no_tasks_match_filters_title')"
                            :description="$t('gantt_no_tasks_match_filters')"
                        />
                    </template>
                </KsCard>
            </div>
        </template>
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onUnmounted} from "vue"
    import {useI18n} from "vue-i18n"
    import {RouterLink, useRoute} from "vue-router"

    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useIsMobile} from "../../composables/useIsMobile"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Warning from "vue-material-design-icons/Alert.vue"
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue"

    import {Duration} from "@kestra-io/topology"
    import {
        State,
        Comparators,
        useRouteFilterPolicy,
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readRouteLevelFilter,
        KsExecutionStatus,
        durationUtils,
        KsFilter as KSFilter,
        KsEmptyState,
        type AppliedFilter,
        type LevelFilterValue,
    } from "@kestra-io/design-system"
    import TaskIcon from "../plugins/TaskIcon.vue"

    import * as FlowUtils from "../../utils/flowUtils"
    import * as Utils from "../../utils/utils"
    import {useToast} from "../../utils/toast"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {usePluginsStore} from "../../stores/plugins"
    import {useGanttExecutionFilter} from "../filter/configurations/ganttExecutionFilter"
    import GanttTaskLogs from "./GanttTaskLogs.vue"
    import TaskRunActions from "./TaskRunActions.vue"
    import ExecutionPending from "./ExecutionPending.vue"
    import ExecutionProgress from "./ExecutionProgress.vue"
    import emptyIllustration from "../../assets/empty_visuals/generic.svg"
    import {buildTaskRunHierarchy} from "../../utils/taskRunHierarchy"
    import {computeTaskBarPercents} from "../../utils/ganttSeries"
    import {useFirstFailure} from "../../composables/useFirstFailure"
    import LoopIterationTree from "./LoopIterationTree.vue"

    import {storageKeys} from "../../utils/constants"
    type TaskRun = ExecutionTaskRun

    interface TaskWrapper {
        task: TaskRun;
        depth: number;
    }

    interface SeriesItem {
        id: string;
        name: string;
        start: number;
        width: number;
        waitPercent: number;
        offset: string;
        color: string;
        task: TaskRun;
        flowId?: string;
        namespace?: string;
        attempts: number;
        depth: number | undefined;
        parentEndPercent?: number;
    }

    withDefaults(defineProps<{
        namespace?: string;
        embed?: boolean;
    }>(), {
        namespace: undefined,
        embed: true,
    })

    const {t} = useI18n()
    const route = useRoute()
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const pluginsStore = usePluginsStore()
    const verticalLayout = useIsMobile()
    const ganttExecutionFilter = useGanttExecutionFilter()

    const TASKRUN_THRESHOLD = 50
    const COLORS = State.color()
    const TASK_TYPES_TO_EXCLUDE = [
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemSplit",
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemMergeOutputs",
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable",
    ]
    const ts = (date: string | Date): number => new Date(date).getTime()

    const series = ref<SeriesItem[]>([])
    const selectedTaskRuns = ref<string[]>([])
    const search = ref<string>("")
    const selectedStates = ref<string[]>([])
    const selectedStatesComparator = ref<Comparators | undefined>(undefined)
    const selectedTaskRunId = ref<string | undefined>(undefined)
    const regularPaintingInterval = ref<ReturnType<typeof setInterval> | undefined>(undefined)
    const expandedFromRoute = ref(false)

    const defaultLogLevel = computed(() => localStorage.getItem(storageKeys.DEFAULT_LOG_LEVEL) || "INFO")
    const {
        effectiveValue: effectiveSelectedLogLevel,
    } = useRouteFilterPolicy<LevelFilterValue>({
        defaultValue: () => ({value: defaultLogLevel.value, direction: "min"}),
        applyDefaultIfMissing: () => true,
        fallbackValue: () => ({value: "TRACE", direction: "min"}),
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
    })
    const execution = computed<Execution | undefined>(() => executionsStore.execution)
    const firstFailure = useFirstFailure(execution, verticalLayout)

    const taskRunsCount = computed<number>(() => execution.value?.taskRunList?.length ?? 0)

    const tasksSummary = computed<string>(() => {
        const counts = new Map<string, number>()
        for (const taskRun of execution.value?.taskRunList ?? []) {
            const state = taskRun.state?.current
            if (state) counts.set(state, (counts.get(state) ?? 0) + 1)
        }
        return [...counts.entries()]
            .map(([state, count]) => `${count} ${state === State.SUCCESS ? "Succeeded" : state.toLowerCase()}`)
            .join(", ")
    })

    const copyAllLogs = (): void => {
        executionsStore
            .downloadLogs({executionId: execution.value!.id})
            .then((response: unknown) => {
                Utils.copy(response as string)
                toast.success(t("copied"))
            })
    }

    const start = computed<number>(() => {
        return execution.value?.state?.histories?.[0] ? ts(execution.value.state.histories[0].date) : 0
    })

    const taskRunsWithHistories = computed(() =>
        ((execution.value?.taskRunList ?? []) as TaskRun[]).filter(tr => (tr.state?.histories?.length ?? 0) > 0),
    )

    const tasks = computed<TaskWrapper[]>(() =>
        buildTaskRunHierarchy(
            taskRunsWithHistories.value,
            (n1, n2) => ts(n1.state.histories[0].date) - ts(n2.state.histories[0].date),
        ),
    )

    const taskTypeByTaskRun = computed<Array<[TaskRun, string | undefined]>>(() => {
        return series.value.map(serie => [serie.task, taskType(serie.task)])
    })

    const taskTypeByTaskRunId = computed<Record<string, string | undefined>>(() => {
        return Object.fromEntries(
            taskTypeByTaskRun.value.map(([taskRun, taskTypeVal]) => [taskRun.id, taskTypeVal]),
        )
    })

    const forEachItemsTaskRunIds = computed<TaskRun[]>(() => {
        return taskTypeByTaskRun.value
            .filter(([, taskTypeVal]) =>
                taskTypeVal === "io.kestra.plugin.core.flow.ForEachItem" ||
                taskTypeVal === "io.kestra.core.tasks.flows.ForEachItem",
            )
            .map(([taskRun]) => taskRun)
    })

    const filteredSeries = computed<SeriesItem[]>(() => {
        const normalizedSearch = search.value?.trim()?.toLowerCase()
        return series.value
            .filter(serie => !TASK_TYPES_TO_EXCLUDE.includes(taskTypeByTaskRunId.value[serie.task.id] ?? ""))
            .filter((serie) => {
                if (normalizedSearch) {
                    const searchText = [
                        serie.name,
                        serie.id,
                        serie.task?.value,
                    ]
                        .filter(Boolean)
                        .join(" ")
                        .toLowerCase()

                    if (!searchText.includes(normalizedSearch)) {
                        return false
                    }
                }

                if (selectedTaskRunId.value && serie.id !== selectedTaskRunId.value) {
                    return false
                }

                if (selectedStates.value.length > 0) {
                    const isInSelectedStates = selectedStates.value.includes(serie.task?.state?.current)
                    if (selectedStatesComparator.value === Comparators.NOT_IN) {
                        return !isInSelectedStates
                    }
                    return isInSelectedStates
                }

                return true
            })
    })

    const isQueued = computed<boolean>(() => execution.value?.state?.current === "QUEUED")

    const seriesById = computed(() => new Map(filteredSeries.value.map((item) => [item.id, item])))

    const childrenByTaskId = computed(() => {
        const children = new Set<string>()

        for (const item of filteredSeries.value) {
            if (item.task.parentTaskRunId) {
                children.add(item.task.parentTaskRunId)
            }
        }

        return children
    })

    const lastChildByParent = computed(() => {
        const lastChild = new Map<string, string>()

        for (const item of filteredSeries.value) {
            lastChild.set(item.task.parentTaskRunId ?? "__root__", item.id)
        }

        return lastChild
    })

    const hasChildren = (item: SeriesItem): boolean =>
        childrenByTaskId.value.has(item.id)

    const isLastSibling = (item: SeriesItem): boolean =>
        lastChildByParent.value.get(item.task.parentTaskRunId ?? "__root__") === item.id

    const continuingRailDepths = (item: SeriesItem): number[] => {
        const depths: number[] = []
        let cursor = item
        let ancestor = seriesById.value.get(cursor.task.parentTaskRunId ?? "")

        while (ancestor) {
            if (!isLastSibling(cursor)) {
                depths.push(ancestor.depth ?? 0)
            }

            cursor = ancestor
            ancestor = seriesById.value.get(cursor.task.parentTaskRunId ?? "")
        }

        return depths
    }

    const rowBlocks = computed(() => {
        const blocks = new Map<string, {isAlt: boolean; isStart: boolean}>()
        const previousSiblingByParent = new Map<string, SeriesItem>()
        let blockIndex = 0

        filteredSeries.value.forEach((item, index) => {
            const parentKey = item.task.parentTaskRunId ?? "__root__"
            const previousSibling = previousSiblingByParent.get(parentKey)
            const isStart = index > 0 && (hasChildren(item) || (previousSibling !== undefined && hasChildren(previousSibling)))

            if (isStart) {
                blockIndex++
            }

            blocks.set(item.id, {isAlt: blockIndex % 2 === 1, isStart})
            previousSiblingByParent.set(parentKey, item)
        })

        return blocks
    })

    const isProgressing = computed<boolean>(() => execution.value?.state?.current === State.RUNNING)

    // Supporting line shown under the status badge when the Gantt has no task runs to plot.
    const emptyStateHint = computed<string>(() => {
        const current = execution.value?.state?.current
        const isPending = current === "CREATED" || current === "QUEUED"
        // The execution reached a terminal state (e.g. cancelled or failed by a
        // concurrency limit) before any task started — nothing will ever be plotted.
        if (current && !isPending && !State.isRunning(current)) {
            return t("gantt_no_tasks_executed")
        }
        // The execution is starting / running but its first task run has not been created yet.
        return `${t("no_tasks_running")}\n${t("execution_starts_progress")}`
    })

    const hasValidDate = computed<boolean>(() => isFinite(delta()))

    function delta(): number {
        return stop() - start.value
    }

    function stop(): number {
        if (!execution.value?.state || State.isRunning(execution.value.state.current)) {
            return +new Date()
        }

        return Math.max(
            ...taskRunsWithHistories.value.map(r => {
                const lastIndex = r.state.histories.length - 1
                return ts(r.state.histories[lastIndex].date)
            }),
        )
    }

    function compute(): void {
        computeSeries()
    }

    function computeSeries(): void {
        if (!execution.value) {
            return
        }

        const newSeries: SeriesItem[] = []
        const executionDelta = delta()

        const barInputs = tasks.value.map(({task}) => {
            const stopTs = State.isRunning(task.state.current)
                ? ts(new Date())
                : ts(task.state.histories[task.state.histories.length - 1].date)
            return {
                id: task.id,
                parentTaskRunId: task.parentTaskRunId,
                startTs: ts(task.state.histories[0].date),
                stopTs,
            }
        })
        const barPercentsById = Object.fromEntries(
            computeTaskBarPercents(barInputs, start.value, executionDelta).map((p) => [p.id, p]),
        )

        for (const taskWrapper of tasks.value) {
            const task = taskWrapper.task
            const startTs = ts(task.state.histories[0].date)

            const runningEntry = task.state.histories.find(h => h.state === State.RUNNING)
            const waitPercent = runningEntry
                ? Math.max(0, ((ts(runningEntry.date) - startTs) / executionDelta) * 100)
                : 0

            const barPercents = barPercentsById[task.id]
            let width = barPercents.width
            if (State.isRunning(task.state.current)) {
                width = ((stop() - startTs) / executionDelta) * 100
            }

            const seriesItem: SeriesItem = {
                id: task.id,
                name: task.taskId,
                start: barPercents.start,
                width,
                waitPercent,
                offset: `+${formatDuration(startTs - start.value)}`,
                color: COLORS[task.state.current],
                task,
                flowId: task.flowId,
                namespace: task.namespace,
                attempts: task.attempts ? task.attempts.length : 1,
                depth: taskWrapper.depth,
                parentEndPercent: barPercents.parentEndPercent,
            }
            newSeries.push(seriesItem)
        }
        series.value = newSeries
    }

    function formatDuration(ms: number): string {
        return durationUtils.humanDuration(ms / 1000, {maxDecimalPoints: 2, units: ["h", "m", "s"], delimiter: " "})
    }

    function onTaskSelect(taskRunId: string): void {
        if (selectedTaskRuns.value.includes(taskRunId)) {
            selectedTaskRuns.value = selectedTaskRuns.value.filter(id => id !== taskRunId)
            return
        }
        selectedTaskRuns.value.push(taskRunId)
    }

    function onFilterChange(filters: AppliedFilter[]): void {
        const stateFilter = filters.find((filter) => filter.key === "state")
        if (stateFilter) {
            selectedStatesComparator.value = stateFilter.comparator
            selectedStates.value = (
                Array.isArray(stateFilter.value) ? stateFilter.value : [stateFilter.value]
            ).filter(Boolean) as string[]
        } else {
            selectedStatesComparator.value = undefined
            selectedStates.value = []
        }

        const taskFilter = filters.find((filter) => filter.key === "task")
        selectedTaskRunId.value = taskFilter
            ? (Array.isArray(taskFilter.value) ? taskFilter.value[0] : taskFilter.value) as string | undefined
            : undefined
    }

    function taskType(taskRun: TaskRun): string | undefined {
        const task = FlowUtils.findTaskById(executionsStore.flow, taskRun.taskId)
        return task?.type
    }

    function taskBarStyle(item: SeriesItem): Record<string, string> {
        const end = item.parentEndPercent ?? item.start + item.width
        const barStart = item.start + item.waitPercent
        const width = Math.max(end - barStart, 3)
        return {
            left: `${Math.max(0, Math.min(barStart, 100 - width))}%`,
            width: `${width}%`,
            background: item.color,
        }
    }

    watch(
        execution,
        (newValue) => {
            if (!newValue?.state?.current || !State.isRunning(newValue.state.current)) {
                clearInterval(regularPaintingInterval.value)
                regularPaintingInterval.value = undefined
                compute()
            } else if (regularPaintingInterval.value === undefined) {
                regularPaintingInterval.value = setInterval(
                    compute,
                    taskRunsCount.value < TASKRUN_THRESHOLD ? 40 : 500,
                )
            }
        },
        {immediate: true},
    )

    watch(
        forEachItemsTaskRunIds,
        (newValue, oldValue) => {
            if (newValue.length > 0) {
                const newEntriesAmount = newValue.length - (oldValue?.length ?? 0)
                for (let i = newValue.length - newEntriesAmount; i < newValue.length; i++) {
                    selectedTaskRuns.value.push(newValue[i].id)
                }
            }
        },
        {immediate: true},
    )

    /** `autoExpandGantt` route query: `true` (all), `failed`, or a comma-separated task id list. */
    function applyAutoExpandFromRoute(currentExecution: Execution | undefined) {
        const autoExpand = route.query.autoExpandGantt
        if (typeof autoExpand !== "string" || !autoExpand) {
            return
        }
        if (!currentExecution?.taskRunList || expandedFromRoute.value) {
            return
        }

        const taskIds = autoExpand === "true" || autoExpand === "failed"
            ? undefined
            : autoExpand.split(",").map((id) => id.trim()).filter(Boolean)

        const taskRuns = autoExpand === "failed"
            ? currentExecution.taskRunList.filter((taskRun: TaskRun) => taskRun.state?.current === "FAILED")
            : taskIds
                ? currentExecution.taskRunList.filter((taskRun: TaskRun) => taskIds.includes(taskRun.taskId))
                : currentExecution.taskRunList

        if (taskRuns.length) {
            selectedTaskRuns.value = taskRuns.map((taskRun: TaskRun) => taskRun.id)
            expandedFromRoute.value = true
        }
    }

    watch(() => route.query.autoExpandGantt,() => applyAutoExpandFromRoute(execution.value))

    watch(
        execution,
        (newExecution) => {
            applyAutoExpandFromRoute(newExecution)
        },
        {immediate: true},
    )

    onUnmounted(() => {
        clearInterval(regularPaintingInterval.value)
    })
</script>

<style scoped>
    .gantt-progress {
        margin-bottom: var(--ks-spacing-4);
    }

    .task-wrapper {
        display: flex;
        flex-direction: column;
    }

    .kel-card {
        --gantt-offset-width: 5rem;
        --gantt-bar-width: 12rem;
        --gantt-duration-width: 9rem;
        --gantt-state-width: 8rem;
        padding: 0;

        &.is-compact {
            --gantt-bar-width: 6rem;
            --gantt-duration-width: 0rem;
        }

        & :deep(.kel-card__header) {
            padding: 0;
            font-size: var(--ks-font-size-sm);

            .gantt-header {
                display: flex;
                flex-direction: column;

                .top {
                    min-height: 48px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: var(--ks-spacing-4);
                    padding: 0 var(--ks-spacing-3);
                    border-bottom: 1px solid var(--ks-border-default);
                    font-size: var(--ks-font-size-xs);

                    .summary {
                        display: flex;
                        align-items: center;
                        gap: var(--ks-spacing-2);

                        .item {
                            display: inline-flex;
                            align-items: center;
                            gap: var(--ks-spacing-3);
                        }

                        .label,
                        .separator {
                            color: var(--ks-text-secondary);
                        }

                        .value {
                            color: var(--ks-text-primary);
                            text-transform: capitalize;
                        }
                    }

                    .actions {
                        display: inline-flex;
                        align-items: center;
                        gap: var(--ks-spacing-3);

                        .copy-logs {
                            font-size: var(--ks-font-size-sm);
                            color: var(--ks-text-secondary);

                            &:hover {
                                color: var(--ks-text-primary);
                            }
                        }
                    }
                }

                .bottom {
                    min-height: 30px;
                    display: flex;
                    align-items: center;
                    font-weight: normal;
                    background: var(--ks-bg-surface);

                    .scale {
                        display: flex;
                        justify-content: space-between;
                        width: var(--gantt-bar-width);
                        padding: 0 var(--ks-spacing-1);
                        margin-left: auto;
                        margin-right: calc(var(--ks-spacing-8) + var(--gantt-duration-width) + var(--gantt-state-width));
                        color: var(--ks-text-secondary);
                    }
                }
            }
        }

        & :deep(.kel-card__body) {
            padding: 0;

            .vue-recycle-scroller {
                max-height: calc(100vh - 223px);
                max-height: calc(100dvh - 223px);

                &::-webkit-scrollbar {
                    width: 5px;
                }

                &::-webkit-scrollbar-track {
                    background: var(--ks-bg-base);
                }

                &::-webkit-scrollbar-thumb {
                    background: var(--ks-border-default);
                    border-radius: 5px;
                }
            }

            .task-details__inner {
                padding: var(--ks-spacing-2);
            }

            .gantt-row {
                --gantt-band-bg: color-mix(in srgb, var(--ks-border-default) 60%, var(--ks-dropdown-bg));

                display: flex;
                align-items: center;
                position: relative;
                padding-right: var(--ks-spacing-8);
                background: var(--ks-dropdown-bg);
                border-top: 1px solid var(--ks-border-default);

                &.gantt-block-alt {
                    background: var(--gantt-band-bg);
                }

                &.gantt-block-start {
                    border-top-color: var(--ks-border-strong);
                }

                &.is-expanded {
                    background: var(--ks-dropdown-bg-active);
                }

                & * {
                    transition: none !important;
                    animation: none !important;
                }

                & > * {
                    padding: 1rem .25rem;
                }

                .gantt-rails {
                    position: absolute;
                    inset: calc(-1 * var(--ks-spacing-px)) 0 0;
                    pointer-events: none;
                }

                .gantt-rail {
                    position: absolute;
                    top: 0;
                    bottom: 0;
                    left: calc(var(--ks-spacing-3) + var(--rail-depth, 0) * var(--ks-spacing-5));
                    border-left: var(--ks-spacing-px) solid var(--ks-border-strong);
                }

                .gantt-rail-outgoing {
                    top: calc(50% + var(--ks-spacing-2));
                }

                .gantt-rail-elbow {
                    bottom: 50%;
                    width: calc(var(--ks-spacing-5) - var(--ks-spacing-2));
                    border-bottom: var(--ks-spacing-px) solid var(--ks-border-strong);
                }

                .gantt-chevron {
                    display: inline-flex;
                    flex-shrink: 0;
                    transform: translateX(calc(var(--depth, 0) * var(--ks-spacing-5)));
                }

                .task-label {
                    flex: 1 1 0;
                    min-width: 6rem;
                    display: flex;
                    align-items: center;
                    gap: var(--ks-spacing-4);
                    padding-left: calc(var(--depth, 0) * var(--ks-spacing-5));

                    code {
                        color: var(--ks-text-primary);
                    }
                }

                .task-icon-box {
                    box-sizing: content-box;
                    flex-shrink: 0;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    width: 1.5rem;
                    height: 1.5rem;
                    padding: var(--ks-spacing-1);
                    border: 1px solid var(--ks-border-default);
                    border-radius: 0.5rem;
                    background: var(--ks-white);
                }

                .task-name {
                    display: inline-block;
                    overflow-wrap: anywhere;
                    vertical-align: middle;

                    code {
                        font-size: var(--ks-font-size-sm);
                        color: var(--ks-text-primary);
                    }

                    small {
                        margin-left: 5px;
                        font-family: var(--kel-font-family-monospace);
                        font-size: var(--ks-font-size-xs);
                    }
                }

                .attempt_warn {
                    margin-right: var(--ks-spacing-4);
                    color: var(--ks-text-warning);
                    vertical-align: middle;
                }

                .task-offset {
                    flex: 0 0 var(--gantt-offset-width);
                    text-align: end;
                    white-space: nowrap;
                    font-family: var(--kel-font-family-monospace);
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-secondary);
                }

                .task-state {
                    flex: 0 0 var(--gantt-state-width);
                }

                .task-duration {
                    flex: 0 0 var(--gantt-duration-width);
                    min-width: 0;
                    overflow: hidden;

                    small {
                        white-space: nowrap;
                        font-family: var(--kel-font-family-monospace);
                        font-size: var(--ks-font-size-xs);
                        color: var(--ks-text-primary);
                    }
                }

                .task-actions {
                    position: absolute;
                    right: var(--ks-spacing-2);
                    top: 50%;
                    transform: translateY(-50%);
                    padding: 0;
                }

                .task-bar {
                    flex: 0 0 var(--gantt-bar-width);
                    position: relative;
                    height: var(--ks-spacing-2);
                    padding: 0;
                    margin: 0 var(--ks-spacing-1);
                    border-radius: var(--ks-spacing-1);
                    background: var(--ks-bg-active);
                }

                .task-progress {
                    position: absolute;
                    top: 0;
                    bottom: 0;
                    border-radius: var(--ks-spacing-1);
                }
            }

            .task-details {
                interpolate-size: allow-keywords;
                overflow: hidden;
                background: var(--ks-dropdown-bg-active);
            }

            .expand-enter-active,
            .expand-leave-active {
                transition: height 150ms ease;
            }

            .expand-enter-from,
            .expand-leave-to {
                height: 0;
            }
        }
    }

    .no-border {
        border: none !important;
    }

    @media (max-width: 767px) {
        #gantt .gantt-row {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto auto auto;
            grid-template-areas:
                "label duration state actions"
                "bar bar bar bar"
                "failure failure failure failure";
            align-items: center;
            gap: var(--ks-spacing-2) var(--ks-spacing-3);
            padding: var(--ks-spacing-3);

            > * {
                padding: 0;
            }

            .task-label {
                grid-area: label;
                gap: var(--ks-spacing-2);
            }

            .task-name {
                display: block;
                max-width: none;
                white-space: normal;
                overflow: visible;
                overflow-wrap: anywhere;
            }

            .task-attempts:empty {
                display: none;
            }

            .task-duration {
                grid-area: duration;
            }

            .task-state {
                grid-area: state;
            }

            .task-actions {
                position: static;
                grid-area: actions;
                transform: none;
            }

            .task-bar {
                grid-area: bar;
            }

            .task-failure {
                grid-area: failure;
                padding: var(--ks-spacing-2) var(--ks-spacing-3);
            }
        }

        #gantt .gantt-header .top {
            flex-wrap: wrap;
            padding: var(--ks-spacing-2) var(--ks-spacing-3);
        }

        #gantt .gantt-header .bottom .scale {
            flex: 1 1 auto;
            width: auto;
            margin: 0 calc(var(--ks-spacing-3) + var(--ks-spacing-1));
        }

        .copy-logs-label {
            display: none;
        }
    }

    .task-failure {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-error);
        border: 1px solid var(--ks-border-error);
        border-radius: var(--ks-radius-base);
        cursor: default;
    }

    .task-failure-link {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        color: var(--ks-text-error);
        font-weight: var(--ks-font-weight-bold);
        text-decoration: none;
    }

    .task-failure-message {
        margin: 0;
        max-width: 100%;
        color: var(--ks-text-error);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        white-space: normal;
        overflow-wrap: anywhere;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
    }

    .gantt-empty-status {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        font-weight: 600;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-xl);
    }

    .gantt-empty-hint {
        display: block;
        margin-top: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
        white-space: pre-line;
        font-size: var(--ks-font-size-md);
    }

    :deep(.vue-recycle-scroller__item-view) {
        margin-bottom: 10px;
    }

    .cursor-icon {
        cursor: pointer;
        color: var(--ks-icon-muted);
    }

    .task-logs {
        max-height: 100%;
        margin-left: var(--ks-spacing-4);
        margin-right: var(--ks-spacing-4);
    }

    :deep(.log-wrapper) {
        .vue-recycle-scroller__item-view {
            border-bottom: none;
            margin-bottom: 0;
        }

        & > .vue-recycle-scroller__item-wrapper > .vue-recycle-scroller__item-view > div {
            border-radius: var(--kel-border-radius-round);
        }
    }
</style>
