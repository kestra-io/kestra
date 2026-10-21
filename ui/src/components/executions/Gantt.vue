<template>
    <div class="gantt-root" :style="{'--execution-banner-height': `${bannerHeight}px`}">
        <ExecutionSummaryCard v-if="props.showBanner && execution" v-model:height="bannerHeight" :execution />
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
                        refresh: {shown: true, callback: refresh}
                    }"
                    @search="search = $event"
                    @filter="onFilterChange"
                />
                <div
                    class="gantt-stage"
                    :class="{'has-panel': hasPanel}"
                    data-test="gantt-stage"
                >
                    <KsSplitter :layout="verticalLayout ? 'vertical' : 'horizontal'" class="gantt-splitter">
                        <KsSplitterPanel :min="verticalLayout ? undefined : '35%'">
                            <KsCard
                                id="gantt"
                                class="gantt-card"
                                :bodyStyle="hasPanel ? PANEL_CARD_BODY_STYLE : undefined"
                                data-onboarding-target="execution-gantt"
                                shadow="never"
                                :class="{'no-border': !hasValidDate, 'is-compact': verticalLayout}"
                            >
                                <template #header v-if="hasValidDate">
                                    <div class="gantt-header">
                                        <div class="top">
                                            <div class="summary">
                                                <KsButton
                                                    v-if="hasFailure"
                                                    class="first-failure"
                                                    link
                                                    type="primary"
                                                    data-test="gantt-first-failure"
                                                    @click="goToFirstFailure"
                                                >
                                                    {{ $t("gantt_go_to_first_failure") }}
                                                    <KsIcon size="sm"><ArrowRight /></KsIcon>
                                                </KsButton>
                                            </div>
                                            <div class="actions">
                                                <KsButton
                                                    :icon="FilterVariant"
                                                    size="small"
                                                    :type="tree.failedOnly.value ? 'primary' : undefined"
                                                    :aria-pressed="tree.failedOnly.value"
                                                    data-test="gantt-failed-only"
                                                    @click="tree.failedOnly.value = !tree.failedOnly.value"
                                                >
                                                    {{ $t("gantt_failed_only") }}
                                                </KsButton>
                                            </div>
                                        </div>
                                        <div class="bottom">
                                            <div class="scale" data-test="gantt-scale">
                                                <span>{{ formatDuration(0) }}</span>
                                                <span>{{ formatDuration(delta) }}</span>
                                            </div>
                                        </div>
                                    </div>
                                </template>
                                <template #default>
                                    <DynamicScroller
                                        v-if="filteredSeries.length > 0"
                                        class="gantt-scroller"
                                        :items="filteredSeries"
                                        :minItemSize="44"
                                        keyField="id"
                                        :buffer="0"
                                        :updateInterval="0"
                                        role="treegrid"
                                        :aria-label="$t('gantt_tree_label')"
                                    >
                                        <template #default="{item, index, active}">
                                            <DynamicScrollerItem
                                                :item="item"
                                                :active="active"
                                                :data-index="index"
                                            >
                                                <GanttRow
                                                    :nodeKey="nodeOf(item).key"
                                                    :label="item.name"
                                                    :ancestors="nodeOf(item).ancestors"
                                                    :offset="item.offset"
                                                    :barStyle="taskBarStyle(item)"
                                                    :state="item.task.state.current"
                                                    :selected="tree.selected.value?.key === nodeOf(item).key"
                                                    :expandable="nodeOf(item).kind === 'loop'"
                                                    :expanded="tree.isExpanded(nodeOf(item).key)"
                                                    @select="tree.select(nodeOf(item))"
                                                    @toggle="tree.toggleExpanded(nodeOf(item).key)"
                                                >
                                                    <template #label>
                                                        <GanttTaskLabel
                                                            :name="item.name"
                                                            :value="item.task?.value"
                                                            :taskType="taskTypeByTaskRunId[item.id]"
                                                            :attempts="item.attempts"
                                                        >
                                                            <GanttLoopChips
                                                                v-if="nodeOf(item).kind === 'loop'"
                                                                :key="nodeOf(item).key"
                                                                :loop="loopRefOf(nodeOf(item))"
                                                                :state="item.task.state.current"
                                                                :nodeKey="nodeOf(item).key"
                                                            />
                                                        </GanttTaskLabel>
                                                    </template>
                                                    <template #duration>
                                                        <Duration :histories="item.task.state.histories" :attemptCount="item.attempts" :subject="item.name" />
                                                    </template>
                                                    <template #actions>
                                                        <TaskRunActions
                                                            :taskRun="item.task"
                                                            :taskType="taskTypeByTaskRunId[item.task.id]"
                                                            :execution="execution"
                                                            :flow="executionsStore.flow"
                                                        />
                                                    </template>
                                                    <template v-if="verticalLayout && item.id === firstFailure.failedTaskRun.value?.id" #failure>
                                                        <div class="task-failure" data-test="gantt-row-failure" @click.stop @keydown.stop>
                                                            <p v-if="firstFailure.message.value" class="task-failure-message">
                                                                {{ firstFailure.message.value }}
                                                            </p>
                                                            <RouterLink class="task-failure-link" :to="firstFailure.logsRoute.value">
                                                                {{ $t("execution_mobile.open_logs") }}
                                                                <ArrowRight />
                                                            </RouterLink>
                                                        </div>
                                                    </template>
                                                </GanttRow>
                                                <LoopIterationTree
                                                    v-if="nodeOf(item).kind === 'loop' && tree.isExpanded(nodeOf(item).key)"
                                                    :key="`${execution!.id}-${item.task.taskId}-${item.id}`"
                                                    :node="nodeOf(item)"
                                                />
                                            </DynamicScrollerItem>
                                        </template>
                                    </DynamicScroller>
                                    <KsNoData
                                        v-else
                                        :title="$t('gantt_no_tasks_match_filters_title')"
                                        :description="$t('gantt_no_tasks_match_filters')"
                                    />
                                </template>
                            </KsCard>
                        </KsSplitterPanel>
                        <KsSplitterPanel
                            v-if="hasPanel"
                            v-model:size="panelSize"
                            :min="verticalLayout ? undefined : '25%'"
                            :max="verticalLayout ? undefined : '70%'"
                        >
                            <div class="gantt-run-panel-slot">
                                <GanttRunPanel />
                            </div>
                        </KsSplitterPanel>
                    </KsSplitter>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onUnmounted, provide} from "vue"
    import {useI18n} from "vue-i18n"
    import {RouterLink, useRoute} from "vue-router"

    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {useEventListener} from "@vueuse/core"
    import {useIsMobile} from "../../composables/useIsMobile"
    import {useFirstFailure} from "../../composables/useFirstFailure"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import FilterVariant from "vue-material-design-icons/FilterVariant.vue"
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue"

    import {Duration} from "@kestra-io/topology"
    import {asProblem} from "@kestra-io/kestra-sdk"
    import {
        State,
        Comparators,
        useRouteFilterPolicy,
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readRouteLevelFilter,
        KsButton,
        KsCard,
        KsExecutionStatus,
        KsIcon,
        KsNoData,
        KsSplitter,
        KsSplitterPanel,
        KsFilter as KSFilter,
        KsEmptyState,
        type AppliedFilter,
        type LevelFilterValue,
    } from "@kestra-io/design-system"

    import * as FlowUtils from "../../utils/flowUtils"
    import {useToast} from "../../utils/toast"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {useGanttExecutionFilter} from "../filter/configurations/ganttExecutionFilter"
    import TaskRunActions from "./TaskRunActions.vue"
    import ExecutionPending from "./ExecutionPending.vue"
    import ExecutionSummaryCard from "./components/ExecutionSummaryCard.vue"
    import ExecutionProgress from "./ExecutionProgress.vue"
    import emptyIllustration from "../../assets/empty_visuals/generic.svg"
    import {buildTaskRunHierarchy} from "../../utils/taskRunHierarchy"
    import {computeTaskBarPercents} from "../../utils/ganttSeries"
    import LoopIterationTree from "./LoopIterationTree.vue"
    import {isReportedCentrally, type KestraHttpError} from "../../utils/kestraHttp"
    import GanttRow from "./gantt/GanttRow.vue"
    import GanttTaskLabel from "./gantt/GanttTaskLabel.vue"
    import GanttLoopChips from "./gantt/GanttLoopChips.vue"
    import GanttRunPanel from "./gantt/GanttRunPanel.vue"
    import {
        createTaskRunNode,
        firstFailedLeaf,
        indexTaskRuns,
        isFailed,
        loopRefOf,
        owningIteration,
        provideGanttTree,
        taskRunAncestors,
        type TaskRunNode,
    } from "./gantt/ganttTree"
    import {GANTT_TIMELINE, clampedBarStyle, formatGanttDuration as formatDuration} from "./ganttTimeline"

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

    const props = withDefaults(defineProps<{
        namespace?: string;
        embed?: boolean;
        showBanner?: boolean;
    }>(), {
        namespace: undefined,
        embed: true,
        showBanner: false,
    })

    const {t} = useI18n()
    const route = useRoute()
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const verticalLayout = useIsMobile()
    const ganttExecutionFilter = useGanttExecutionFilter()

    const bannerHeight = ref(0)

    const TASKRUN_THRESHOLD = 50
    const PANEL_CARD_BODY_STYLE = {flex: "1", minHeight: "0", padding: "0"}
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
    const now = ref(Date.now())

    const series = ref<SeriesItem[]>([])
    const panelSize = ref("40%")
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
    const tree = provideGanttTree({compact: verticalLayout, levelFilter: effectiveSelectedLogLevel})
    const hasPanel = computed(() => Boolean(tree.selected.value && tree.panelOpen.value))

    const taskRunsCount = computed<number>(() => execution.value?.taskRunList?.length ?? 0)

    const hasFailure = computed<boolean>(() =>
        (execution.value?.taskRunList ?? []).some((taskRun: TaskRun) => isFailed(taskRun.state?.current)),
    )

    function goToFirstFailure(): void {
        const failed = firstFailedLeaf((execution.value?.taskRunList ?? []) as TaskRun[])
        const node = failed ? nodeByTaskRunId.value.get(failed.id) : undefined
        if (node) tree.revealFirstFailure(node)
    }

    function onPanelKeydown(event: KeyboardEvent): void {
        const selected = tree.selected.value
        if (event.defaultPrevented || !tree.panelOpen.value || !selected) {
            return
        }
        if (event.target instanceof Element && event.target.closest("input, textarea, [contenteditable='true']")) {
            return
        }
        if (event.key === "Escape") {
            tree.closePanel()
        } else if (owningIteration(selected) && (event.key === "[" || event.key === "]")) {
            tree.stepIteration(selected, event.key === "[" ? -1 : 1)
        }
    }

    useEventListener(document, "keydown", onPanelKeydown)

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

    const nodeByTaskRunId = computed<Map<string, TaskRunNode>>(() => {
        const currentExecution = execution.value
        if (!currentExecution) {
            return new Map()
        }
        const taskRunsById = indexTaskRuns(series.value.map(serie => serie.task))
        return new Map(series.value.map(serie => [
            serie.id,
            createTaskRunNode(currentExecution, serie.task, taskTypeByTaskRunId.value[serie.id], taskRunAncestors(taskRunsById, serie.task, [])),
        ]))
    })

    const nodeOf = (serie: SeriesItem): TaskRunNode => nodeByTaskRunId.value.get(serie.id)!

    const taskTypeByTaskRunId = computed<Record<string, string | undefined>>(() => {
        return Object.fromEntries(series.value.map(serie => [serie.task.id, taskType(serie.task)]))
    })

    const filteredSeries = computed<SeriesItem[]>(() => {
        const normalizedSearch = search.value?.trim()?.toLowerCase()
        return series.value
            .filter(serie => !TASK_TYPES_TO_EXCLUDE.includes(taskTypeByTaskRunId.value[serie.task.id] ?? ""))
            .filter(serie => !tree.failedOnly.value || tree.hasFailure(nodeOf(serie)))
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

    const hasValidDate = computed<boolean>(() => isFinite(delta.value))

    const stopTimestamp = computed<number>(() => {
        if (!execution.value?.state || State.isRunning(execution.value.state.current)) {
            return now.value
        }

        return Math.max(
            ...taskRunsWithHistories.value.map(r => {
                const lastIndex = r.state.histories.length - 1
                return ts(r.state.histories[lastIndex].date)
            }),
        )
    })

    const delta = computed<number>(() => stopTimestamp.value - start.value)

    provide(GANTT_TIMELINE, {start, delta})

    function compute(): void {
        now.value = Date.now()
        computeSeries()
    }

    async function refresh(): Promise<void> {
        const executionId = execution.value?.id
        if (!executionId) return

        try {
            await executionsStore.loadExecution({id: executionId})
            compute()
        } catch (error) {
            const httpError = error as KestraHttpError
            if (httpError.response && httpError.status !== 401 && !isReportedCentrally(httpError)) {
                toast.error(asProblem(error)?.detail ?? t("error"))
            }
        }
    }

    function computeSeries(): void {
        if (!execution.value) {
            return
        }

        const newSeries: SeriesItem[] = []
        const executionDelta = delta.value

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
                width = ((stopTimestamp.value - startTs) / executionDelta) * 100
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
        return clampedBarStyle(barStart, end - barStart, item.color)
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

    /** `autoExpandGantt` route query: `true` (first task run), `failed` (first failed), or a comma-separated task id list (first match). */
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
            ? currentExecution.taskRunList.filter((taskRun: TaskRun) => isFailed(taskRun.state?.current))
            : taskIds
                ? currentExecution.taskRunList.filter((taskRun: TaskRun) => taskIds.includes(taskRun.taskId))
                : currentExecution.taskRunList

        const matched = series.value.find(serie => taskRuns.some((taskRun: TaskRun) => taskRun.id === serie.id))
        const node = matched ? nodeOf(matched) : undefined
        if (node) {
            tree.select(node, {reveal: true})
            expandedFromRoute.value = true
        }
    }

    watch(nodeByTaskRunId, (nodes) => {
        const selected = tree.selected.value
        if (selected?.kind !== "taskRun" && selected?.kind !== "loop") return
        if (selected.executionId !== execution.value?.id) return
        const fresh = nodes.get(selected.taskRun.id)
        if (fresh && (fresh.taskRun !== selected.taskRun || fresh.kind !== selected.kind)) tree.selected.value = fresh
    })

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
    .gantt-root {
        display: flex;
        flex-direction: column;
    }

    .gantt-progress {
        margin-bottom: var(--ks-spacing-4);
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

                        .first-failure {
                            font-size: var(--ks-font-size-xs);
                            color: var(--ks-text-link);
                        }
                    }

                    .actions {
                        display: inline-flex;
                        align-items: center;
                        gap: var(--ks-spacing-3);
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
                max-height: max(15rem, calc(100vh - 223px - var(--execution-banner-height, 0px)));
                max-height: max(15rem, calc(100dvh - 223px - var(--execution-banner-height, 0px)));

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
        }
    }

    .no-border {
        border: none !important;
    }

    @media (max-width: 767px) {
        #gantt .gantt-header .top {
            flex-wrap: wrap;
            padding: var(--ks-spacing-2) var(--ks-spacing-3);
        }

        #gantt .gantt-header .bottom .scale {
            flex: 1 1 auto;
            width: auto;
            margin: 0 calc(var(--ks-spacing-3) + var(--ks-spacing-1));
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

    .gantt-stage.has-panel {
        height: max(15rem, calc(100vh - 14rem - var(--execution-banner-height, 0px)));
        height: max(15rem, calc(100dvh - 14rem - var(--execution-banner-height, 0px)));
    }

    .gantt-stage.has-panel .gantt-card {
        height: 100%;
        display: flex;
        flex-direction: column;
        margin-right: var(--ks-spacing-2);
    }

    .gantt-stage.has-panel .gantt-scroller {
        height: 100%;
        max-height: 100%;
    }

    .gantt-run-panel-slot {
        height: 100%;
        padding-left: var(--ks-spacing-2);
    }
</style>
