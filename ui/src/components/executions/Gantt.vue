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
        <div class="gantt-stage">
            <KsCard
                id="gantt"
                data-onboarding-target="execution-gantt"
                shadow="never"
                :class="{'no-border': !hasValidDate}"
            >
                <template #header v-if="hasValidDate">
                    <div class="d-flex">
                        <Duration class="th text-end" :histories="execution.state.histories" />
                        <div v-if="verticalLayout" class="timeline-header">
                            <span class="timeline-start">{{ startTime }}</span>
                            <span class="timeline-end">{{ endTime }}</span>
                        </div>
                        <span v-else class="text-end" v-for="(date, i) in dates" :key="i">
                            {{ date }}
                        </span>
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
                                    <div class="gantt-row d-flex cursor-icon" @click="onTaskSelect(item.id)">
                                        <div v-if="!verticalLayout" class="d-inline-flex">
                                            <ChevronRight v-if="!selectedTaskRuns.includes(item.id)" />
                                            <ChevronDown v-else />
                                        </div>
                                        <KsTooltip placement="top-start">
                                            <template #content>
                                                <code>{{ item.name }}</code>
                                                <small v-if="item.task && item.task.value"><br>{{ item.task.value }}</small>
                                            </template>
                                            <span v-if="verticalLayout" class="task-name">
                                                <code :title="item.name">{{ item.name }}</code>
                                                <small v-if="item.task && item.task.value"> {{ item.task.value }}</small>
                                            </span>
                                            <span v-else>
                                                <code>{{ item.name }}</code>
                                                <small v-if="item.task && item.task.value"> {{ item.task.value }}</small>
                                            </span>
                                        </KsTooltip>
                                        <div>
                                            <KsTooltip v-if="item.attempts > 1" placement="right">
                                                <template #content>
                                                    <span>{{ $t("this_task_has") }} {{ item.attempts }} {{ $t("attempts").toLowerCase() }}.</span>
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
                                                <div
                                                    :style="item.parentEndPercent !== undefined ? {left: `${item.start}%`, width: `${item.parentEndPercent - item.start}%`} : {left: `${item.start}%`, width: `${Math.max(item.width, 3)}%`}"
                                                    class="task-progress"
                                                >
                                                    <KsProgress
                                                        :left="Math.min(item.left, 90)"
                                                        :percentage="Math.max(100 - item.left, 10)"
                                                        :color="item.color"
                                                        :stroke-width="25"
                                                        :striped="item.running"
                                                        :stripedFlow="item.running"
                                                        :showText="false"
                                                    />
                                                </div>
                                            </KsTooltip>
                                        </div>
                                    </div>
                                    <div v-if="selectedTaskRuns.includes(item.id)" class="p-2">
                                        <TaskRunDetails
                                            :taskRunId="item.id"
                                            :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
                                            :levelFilter="effectiveSelectedLogLevel"
                                            @follow="emit('follow', $event)"
                                            :targetFlow="executionsStore.flow"
                                            class="mh-100 mx-3"
                                        />
                                    </div>
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
            @update:modks-value="showOnboardingSuccessPopup = $event"
        />
        <SaveExecuteAnimation
            :modelValue="showSaveExecuteAnimation"
            @update:modks-value="showSaveExecuteAnimation = $event"
            @finished="onSaveExecuteAnimationFinished"
        />
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onUnmounted} from "vue"
    import moment from "moment"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    // @ts-expect-error no types yet
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import {State, durationUtils} from "@kestra-io/design-system"
    // @ts-expect-error no types yet
    import Duration from "../layout/Duration.vue"
    // @ts-expect-error JS module without declarations
    import * as FlowUtils from "../../utils/flowUtils"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import {useBreakpoints, breakpointsElement} from "@vueuse/core"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Warning from "vue-material-design-icons/Alert.vue"
    import ExecutionPending from "./ExecutionPending.vue"
    import OnboardingSuccessPopup from "../onboarding/OnboardingSuccessPopup.vue"
    import SaveExecuteAnimation from "../inputs/SaveExecuteAnimation.vue"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"
    import {Comparators, type AppliedFilter} from "@kestra-io/design-system"
    import {useGanttExecutionFilter} from "../filter/configurations"
    import {
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readRouteLevelFilter,
    } from "@kestra-io/design-system"
    import {useRouteFilterPolicy} from "@kestra-io/design-system"
    import type {LevelFilterValue} from "@kestra-io/design-system"
    import {useExecutionsStore, type Execution} from "../../stores/executions"

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

    // Props
    withDefaults(defineProps<{
        namespace?: string;
        embed?: boolean;
    }>(), {
        namespace: undefined,
        embed: true,
    })

    // Emits
    const emit = defineEmits<{
        follow: [event: unknown];
        "go-to-detail": [event: unknown];
        goToDetail: [event: unknown];
    }>()

    // Composables
    const {t} = useI18n()
    const route = useRoute()
    const executionsStore = useExecutionsStore()
    const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm")
    const ganttExecutionFilter = useGanttExecutionFilter()
    // Constants
    const TASKRUN_THRESHOLD = 50
    const ts = (date: string | Date): number => new Date(date).getTime()
    const colors = State.color()
    const taskTypesToExclude = [
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemSplit",
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemMergeOutputs",
        "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs",
        "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable",
    ]

    // Reactive state
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

    // Log level filter policy
    const defaultLogLevel = computed(() => localStorage.getItem("defaultLogLevel") || "INFO")
    const {effectiveValue: effectiveSelectedLogLevel} = useRouteFilterPolicy<LevelFilterValue>({
        defaultValue: () => ({value: defaultLogLevel.value, direction: "min"}),
        applyDefaultIfMissing: () => true,
        fallbackValue: () => ({value: "TRACE", direction: "min"}),
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
    })

    // Computed properties
    const execution = computed<Execution | undefined>(() => executionsStore.execution)

    const taskRunsCount = computed<number>(() => {
        return execution.value?.taskRunList ? execution.value.taskRunList.length : 0
    })

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
            .filter(serie => !taskTypesToExclude.includes(taskTypeByTaskRunId.value[serie.task.id] ?? ""))
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

    // Methods
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
                color: colors[task.state.current],
                running: State.isRunning(task.state.current),
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

    // Watchers
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

    // Lifecycle
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

            > div {
                > * {
                    padding: .5rem;
                    flex: 1;
                }

                > .th {
                    background-color: var(--ks-bg-tag-hover);
                }

                > :not(.th) {
                    font-weight: normal;
                }

                .timeline-header {
                    flex: 1;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: .5rem;
                    font-weight: normal;

                    .timeline-start, .timeline-end {
                        font-size: var(--ks-font-size-sm);
                        color: var(--ks-text-primary);
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
                * {
                    transition: none !important;
                    animation: none !important;
                }

                > * {
                    padding: 1rem .5rem;
                }

                .ks-tooltip__trigger {
                    flex: 1;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;

                    small {
                        margin-left: 5px;
                        font-family: var(--kel-font-family-monospace);
                        font-size: var(--ks-font-size-xs);
                    }

                    code {
                        font-size: var(--ks-font-size-sm);
                        color: var(--ks-text-primary);
                    }
                }

                .task-name {
                    flex: 1;
                    min-width: 100px;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;

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
                    color: var(--ks-color-warning);
                    vertical-align: middle;
                }

                .task-progress {
                    position: relative;
                    transition: all 0.3s;
                    min-width: 5px;
                }
            }
        }
    }

    .no-border {
        border: none !important;
    }

    // To Separate through Line
    :deep(.vue-recycle-scroller__item-view) {
        border-bottom: 1px solid var(--ks-border-default);
        margin-bottom: 10px;

        &:last-child {
            border-bottom: none;
        }
    }

    .cursor-icon {
        cursor: pointer;
    }

    :deep(.log-wrapper) {
        > .vue-recycle-scroller__item-wrapper > .vue-recycle-scroller__item-view > div {
            border-radius: var(--kel-border-radius-round);
        }
    }
</style>
