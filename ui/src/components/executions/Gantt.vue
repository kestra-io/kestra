<template>
    <ExecutionPending
        v-if="!isExecutionStarted"
        :execution="execution!"
    />
    <template v-else-if="execution && executionsStore.flow">
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
        <QuickFilters
            v-if="!hasComplexFilters"
            :levels="VALUES.LEVELS"
            :level="effectiveSelectedLogLevel?.value"
            :showInterval="false"
            :levelLabel="t('filter.level_log_executions.label')"
            @update:level="(value: string) => setLevelRouteValue({value, direction: 'min'})"
        />
        <div class="gantt-stage">
            <KsCard
                id="gantt"
                data-onboarding-target="execution-gantt"
                shadow="never"
                :class="{'no-border': !hasValidDate}"
            >
                <template #header v-if="hasValidDate">
                    <div class="gantt-header">
                        <div class="top">
                            <div class="summary">
                                <span class="item">
                                    <span class="label">{{ t("total_duration") }}</span>
                                    <Duration class="value" :histories="execution.state.histories" />
                                </span>
                                <span class="separator">/</span>
                                <span class="item">
                                    <span class="label">{{ t("tasks") }}</span>
                                    <span class="value">{{ tasksSummary }}</span>
                                </span>
                            </div>
                            <div class="actions">
                                <KsButton class="copy-logs" :icon="ContentCopy" link @click="copyAllLogs">
                                    {{ t("copy all logs") }}
                                </KsButton>
                                <KsExecutionStatus :status="execution.state.current" />
                            </div>
                        </div>
                        <div class="bottom">
                            <div v-if="verticalLayout" class="timeline">
                                <span class="start">{{ startTime }}</span>
                                <span class="end">{{ endTime }}</span>
                            </div>
                            <span v-else class="tick" v-for="(date, i) in dates" :key="i">
                                {{ date }}
                            </span>
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
                                :sizeDependencies="[selectedTaskRuns]"
                            >
                                <div class="d-flex flex-column">
                                    <div
                                        class="gantt-row d-flex cursor-icon"
                                        :class="{'is-expanded': selectedTaskRuns.includes(item.id)}"
                                        @click="onTaskSelect(item.id)"
                                    >
                                        <div v-if="!verticalLayout" class="d-inline-flex">
                                            <ChevronRight v-if="!selectedTaskRuns.includes(item.id)" />
                                            <ChevronDown v-else />
                                        </div>
                                        <div class="task-label">
                                            <div v-if="taskTypeByTaskRunId[item.id]" class="task-icon-box">
                                                <KsTaskIcon :cls="taskTypeByTaskRunId[item.id]" onlyIcon :icons="pluginsStore.icons" />
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
                                        <div>
                                            <KsTooltip v-if="item.attempts > 1" placement="right">
                                                <template #content>
                                                    <span>{{ t("this_task_has") }} {{ item.attempts }} {{ t("attempts").toLowerCase() }}.</span>
                                                </template>
                                                <Warning class="attempt_warn me-3" />
                                            </KsTooltip>
                                        </div>
                                        <div :style="'width: ' + (100 / (dates.length + 1)) * dates.length + '%'">
                                            <KsTooltip placement="top">
                                                <template #content>
                                                    <span style="white-space: pre-wrap;">
                                                        {{ item.tooltip }}
                                                    </span>
                                                </template>
                                                <div :style="taskBarStyle(item)" class="task-progress">
                                                    <KsProgress
                                                        :left="Math.min(item.left, 90)"
                                                        :percentage="Math.max(100 - item.left, 10)"
                                                        :color="item.color"
                                                        :stroke-width="7"
                                                        :radius="81"
                                                        :striped="item.running"
                                                        :stripedFlow="item.running"
                                                        :showText="false"
                                                    />
                                                </div>
                                            </KsTooltip>
                                        </div>
                                        <div class="task-duration d-none d-md-inline-block">
                                            <small>
                                                <Duration :histories="item.task.state.histories" />
                                            </small>
                                        </div>
                                        <div class="task-actions" @click.stop>
                                            <TaskRunActions
                                                :taskRun="item.task"
                                                :execution="execution"
                                                :flow="executionsStore.flow"
                                                @follow="emit('follow', $event)"
                                            />
                                        </div>
                                    </div>
                                    <Transition name="expand">
                                        <div v-if="selectedTaskRuns.includes(item.id)" class="task-details">
                                            <div class="task-details__inner p-2">
                                                <TaskRunDetails
                                                    :taskRunId="item.id"
                                                    :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
                                                    :levelFilter="effectiveSelectedLogLevel"
                                                    hideTaskHeader
                                                    @follow="emit('follow', $event)"
                                                    :targetFlow="executionsStore.flow"
                                                    class="mh-100 mx-3"
                                                />
                                            </div>
                                        </div>
                                    </Transition>
                                </div>
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </template>
            </KsCard>
        </div>
        <OnboardingSuccessPopup
            :modelValue="showOnboardingSuccessPopup"
            :backdrop="false"
            @update:modelValue="showOnboardingSuccessPopup = $event"
        />
        <SaveExecuteAnimation
            :modelValue="showSaveExecuteAnimation"
            @update:modelValue="showSaveExecuteAnimation = $event"
            @finished="onSaveExecuteAnimationFinished"
        />
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onUnmounted} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"

    import moment from "moment"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Warning from "vue-material-design-icons/Alert.vue"

    import {Duration} from "@kestra-io/topology"
    import {
        State,
        Comparators,
        durationUtils,
        useRouteFilterPolicy,
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readRouteLevelFilter,
        KsExecutionStatus,
        KsTaskIcon,
        KsFilter as KSFilter,
        type AppliedFilter,
        type LevelFilterValue,
    } from "@kestra-io/design-system"

    import * as FlowUtils from "../../utils/flowUtils"
    import * as Utils from "../../utils/utils"
    import {useToast} from "../../utils/toast"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {usePluginsStore} from "../../stores/plugins"
    import {useGanttExecutionFilter} from "../filter/configurations"
    import {useValues} from "../filter/composables/useValues"
    import {useComplexFilters} from "../filter/composables/useComplexFilters"
    import QuickFilters from "../filter/QuickFilters.vue"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import TaskRunActions from "./TaskRunActions.vue"
    import ExecutionPending from "./ExecutionPending.vue"
    import OnboardingSuccessPopup from "../onboarding/OnboardingSuccessPopup.vue"
    import SaveExecuteAnimation from "../inputs/SaveExecuteAnimation.vue"

    interface TaskRun {
        id: string;
        taskId: string;
        parentTaskRunId?: string;
        value?: string;
        flowId?: string;
        namespace?: string;
        outputs?: Record<string, unknown>;
        attempts?: unknown[];
        state: {
            current: string;
            histories: Array<{
                state: string;
                date: string;
            }>;
        };
    }

    interface TaskWrapper {
        task: TaskRun;
        depth: number | undefined;
        children?: TaskWrapper[];
    }

    interface SeriesItem {
        id: string;
        name: string;
        start: number;
        width: number;
        left: number;
        tooltip: string;
        color: string;
        running: boolean;
        task: TaskRun;
        flowId?: string;
        namespace?: string;
        executionId?: string;
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

    const emit = defineEmits<{
        follow: [event: unknown];
        goToDetail: [event: unknown];
    }>()

    const {t} = useI18n()
    const route = useRoute()
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const pluginsStore = usePluginsStore()
    pluginsStore.fetchIcons()
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")
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
    const dates = ref<string[]>([])
    const selectedTaskRuns = ref<string[]>([])
    const search = ref<string>("")
    const selectedStates = ref<string[]>([])
    const selectedStatesComparator = ref<Comparators | undefined>(undefined)
    const selectedTaskRunId = ref<string | undefined>(undefined)
    const regularPaintingInterval = ref<ReturnType<typeof setInterval> | undefined>(undefined)
    const expandedFromRoute = ref(false)
    const showOnboardingSuccessPopup = ref(false)
    const showSaveExecuteAnimation = ref(false)
    const onboardingAnimationPlayed = ref(false)

    const defaultLogLevel = computed(() => localStorage.getItem("defaultLogLevel") || "INFO")
    const {
        effectiveValue: effectiveSelectedLogLevel,
        setRouteValue: setLevelRouteValue,
    } = useRouteFilterPolicy<LevelFilterValue>({
        defaultValue: () => ({value: defaultLogLevel.value, direction: "min"}),
        applyDefaultIfMissing: () => true,
        fallbackValue: () => ({value: "TRACE", direction: "min"}),
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
    })
    const {VALUES} = useValues("logs")
    const {hasComplexFilters} = useComplexFilters()

    const execution = computed<Execution | undefined>(() => executionsStore.execution)

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

    const tasks = computed<TaskWrapper[]>(() => {
        const rootTasks: TaskWrapper[] = []
        const childTasks: TaskWrapper[] = []
        const sortedTasks: TaskWrapper[] = []
        const tasksById: Record<string, TaskWrapper> = {}

        for (const task of (execution.value?.taskRunList || []) as TaskRun[]) {
            const taskWrapper: TaskWrapper = {task, depth: task.parentTaskRunId ? undefined : 0}
            if (task.parentTaskRunId) {
                childTasks.push(taskWrapper)
            } else {
                rootTasks.push(taskWrapper)
            }
            tasksById[task.id] = taskWrapper
        }

        for (let i = 0; i < childTasks.length; i++) {
            const taskWrapper = childTasks[i]
            const parentTask = tasksById[taskWrapper.task.parentTaskRunId!]
            if (parentTask) {
                taskWrapper.depth = parentTask.depth! + 1
                tasksById[taskWrapper.task.id] = taskWrapper
                if (!parentTask.children) {
                    parentTask.children = []
                }
                parentTask.children.push(taskWrapper)
            }
        }

        const nodeStart = (node: TaskWrapper): number => ts(node.task.state.histories[0].date)
        const childrenSort = (nodes: TaskWrapper[]): void => {
            nodes.sort((n1, n2) => (nodeStart(n1) > nodeStart(n2) ? 1 : -1))
            for (const node of nodes) {
                sortedTasks.push(node)
                if (node.children) {
                    childrenSort(node.children)
                }
            }
        }
        childrenSort(rootTasks)
        return sortedTasks
    })

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

    const isExecutionStarted = computed<boolean>(() => {
        return !!execution.value?.state?.current && !["CREATED", "QUEUED"].includes(execution.value.state.current)
    })

    const hasValidDate = computed<boolean>(() => isFinite(delta()))

    const startTime = computed<string>(() => {
        if (!execution.value?.state?.histories?.[0]) return ""
        return moment(execution.value.state.histories[0].date).format("HH:mm:ss")
    })

    const endTime = computed<string>(() => {
        if (!execution.value?.state) return ""
        const endDate = State.isRunning(execution.value.state.current)
            ? new Date()
            : new Date(stop())
        return moment(endDate).format("HH:mm:ss")
    })

    function delta(): number {
        return stop() - start.value
    }

    function stop(): number {
        if (!execution.value?.state || State.isRunning(execution.value.state.current)) {
            return +new Date()
        }

        return Math.max(
            ...(execution.value.taskRunList as TaskRun[] || []).map(r => {
                const lastIndex = r.state.histories.length - 1
                return ts(r.state.histories[lastIndex].date)
            }),
        )
    }

    function compute(): void {
        computeSeries()
        computeDates()
    }

    function computeSeries(): void {
        if (!execution.value) {
            return
        }

        const newSeries: SeriesItem[] = []
        const executionDelta = delta()
        const taskMap: Record<string, SeriesItem> = {}

        for (const taskWrapper of tasks.value) {
            const task = taskWrapper.task
            let stopTs: number
            if (State.isRunning(task.state.current)) {
                stopTs = ts(new Date())
            } else {
                const lastIndex = task.state.histories.length - 1
                stopTs = ts(task.state.histories[lastIndex].date)
            }

            const startTs = ts(task.state.histories[0].date)

            const runningState = task.state.histories.filter(r => r.state === State.RUNNING)
            const left = runningState.length > 0
                ? ((ts(runningState[0].date) - startTs) / (stopTs - startTs) * 100)
                : 0

            const taskStart = startTs - start.value
            const taskStop = stopTs - start.value - taskStart

            const taskDelta = stopTs - startTs

            let tooltip = `${t("duration")} : ${durationUtils.humanDuration(taskDelta / 1000)}`

            if (runningState.length > 0) {
                tooltip += `\n${t("queued duration")} : ${durationUtils.humanDuration((ts(runningState[0].date) - startTs) / 1000)}`
                tooltip += `\n${t("running duration")} : ${durationUtils.humanDuration((stopTs - ts(runningState[0].date)) / 1000)}`
            }

            let width = (taskStop / executionDelta) * 100
            if (State.isRunning(task.state.current)) {
                width = ((stop() - startTs) / executionDelta) * 100
            }

            const startPercent = (taskStart / executionDelta) * 100
            let parentEndPercent: number | undefined = undefined

            if (task.parentTaskRunId && taskMap[task.parentTaskRunId]) {
                const parent = taskMap[task.parentTaskRunId]
                parentEndPercent = parent.start + parent.width
            }

            const seriesItem: SeriesItem = {
                id: task.id,
                name: task.taskId,
                start: startPercent,
                width,
                left,
                tooltip,
                color: COLORS[task.state.current],
                running: Boolean(State.isRunning(task.state.current)),
                task,
                flowId: task.flowId,
                namespace: task.namespace,
                executionId: task.outputs?.executionId as string | undefined,
                attempts: task.attempts ? task.attempts.length : 1,
                depth: taskWrapper.depth,
                parentEndPercent,
            }

            taskMap[task.id] = seriesItem
            newSeries.push(seriesItem)
        }
        series.value = newSeries
    }

    function computeDates(): void {
        const ticks = 5
        const formatDate = (timestamp: number): string => moment(timestamp).format("h:mm:ss")
        const startVal = start.value
        const deltaVal = delta() / ticks
        const newDates: string[] = []
        for (let i = 0; i < ticks; i++) {
            newDates.push(formatDate(startVal + i * deltaVal))
        }
        dates.value = newDates
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
        if (item.parentEndPercent !== undefined) {
            return {left: `${item.start}%`, width: `${item.parentEndPercent - item.start}%`}
        }
        const width = Math.max(item.width, 3)
        return {left: `${Math.max(0, Math.min(item.start, 100 - width))}%`, width: `${width}%`}
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

    watch(
        execution,
        (newExecution) => {
            if (route.query.autoExpandGantt === "true" && newExecution?.taskRunList && !expandedFromRoute.value) {
                selectedTaskRuns.value = newExecution.taskRunList.map(taskRun => taskRun.id)
                expandedFromRoute.value = true
            }

            if (
                route.query.onboardingSuccess === "true" &&
                newExecution?.state?.current === "SUCCESS" &&
                !onboardingAnimationPlayed.value
            ) {
                onboardingAnimationPlayed.value = true
                showSaveExecuteAnimation.value = true
                showOnboardingSuccessPopup.value = true
            }
        },
        {immediate: true},
    )

    function onSaveExecuteAnimationFinished() {
        showOnboardingSuccessPopup.value = true
    }

    onUnmounted(() => {
        clearInterval(regularPaintingInterval.value)
    })
</script>

<style scoped lang="scss">
    .kel-card {
        padding: 0;

        :deep(.kel-card__header) {
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

                    > * {
                        padding: .5rem;
                        padding-right: 2.5rem;
                        flex: 1;
                    }

                    .tick {
                        text-align: end;

                        &:first-child {
                            background: var(--ks-bg-active);
                        }
                    }

                    .timeline {
                        flex: 1;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: .5rem;
                        font-weight: normal;

                        .start,
                        .end {
                            font-size: var(--ks-font-size-sm);
                            color: var(--ks-text-primary);
                        }
                    }
                }
            }
        }

        :deep(.kel-card__body) {
            padding: 0;

            .vue-recycle-scroller {
                max-height: calc(100vh - 223px);

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

            .gantt-row {
                align-items: center;
                position: relative;
                padding-right: var(--ks-spacing-8);
                background: var(--ks-dropdown-bg);

                &.is-expanded {
                    background: var(--ks-dropdown-bg-active);
                }

                * {
                    transition: none !important;
                    animation: none !important;
                }

                > * {
                    padding: 1rem .25rem;
                }

                .task-label {
                    flex: 1;
                    min-width: 0;
                    display: flex;
                    align-items: center;
                    gap: var(--ks-spacing-4);

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
                    max-width: 12rem;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
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

                .attempt_warn{
                    color: var(--ks-text-warning);
                    vertical-align: middle;
                }

                .task-duration {
                    flex-shrink: 0;

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

                .task-progress {
                    position: relative;
                    transition: all 0.3s;
                    min-width: 5px;
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

    :deep(.vue-recycle-scroller__item-view) {
        border-bottom: 1px solid var(--ks-border-default);
        margin-bottom: 10px;

        &:last-child {
            border-bottom: none;
        }
    }

    .cursor-icon {
        cursor: pointer;
        color: var(--ks-icon-muted);
    }

    :deep(.log-wrapper) {
        .vue-recycle-scroller__item-view {
            border-bottom: none;
            margin-bottom: 0;
        }

        > .vue-recycle-scroller__item-wrapper > .vue-recycle-scroller__item-view > div {
            border-radius: var(--kel-border-radius-round);
        }
    }
</style>
