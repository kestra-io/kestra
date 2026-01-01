<template>
    <ExecutionPending
        v-if="!isExecutionStarted"
        :execution="execution"
    />
    <el-card id="gantt" shadow="never" :class="{'no-border': !hasValidDate}" v-else-if="execution && executionsStore.flow">
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
                                <el-tooltip placement="top-start" :persistent="false" transition="el-fade-in-linear" :autoClose="2000" effect="light">
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
                                </el-tooltip>
                                <div>
                                    <el-tooltip v-if="item.attempts > 1" placement="right" :persistent="false" transition="el-fade-in-linear" :autoClose="2000" effect="light">
                                        <template #content>
                                            <span>{{ $t("this_task_has") }} {{ item.attempts }} {{ $t("attempts").toLowerCase() }}.</span>
                                        </template>
                                        <Warning class="attempt_warn me-3" />
                                    </el-tooltip>
                                </div>
                                <div :style="'width: ' + (100 / (dates.length + 1)) * dates.length + '%'">
                                    <el-tooltip placement="top" :persistent="false" transition="el-fade-in-linear" :autoClose="2000" effect="light">
                                        <template #content>
                                            <span style="white-space: pre-wrap;">
                                                {{ item.tooltip }}
                                            </span>
                                        </template>
                                        <div
                                            :style="item.parentEndPercent !== undefined ? {left: `${item.start}%`, width: `${item.parentEndPercent - item.start}%`} : {left: `${item.start}%`, width: `${Math.max(item.width, 3)}%`}"
                                            class="task-progress"
                                        >
                                            <div class="progress">
                                                <div
                                                    :style="{left: `${Math.min(item.left, 90)}%`, width: `${Math.max(100 - item.left, 10)}%`}"
                                                    class="progress-bar"
                                                    :class="'bg-' + item.color + (item.running ? ' progress-bar-striped progress-bar-animated' : '')"
                                                    role="progressbar"
                                                />
                                            </div>
                                        </div>
                                    </el-tooltip>
                                </div>
                            </div>
                            <div v-if="selectedTaskRuns.includes(item.id)" class="p-2">
                                <TaskRunDetails
                                    :taskRunId="item.id"
                                    :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
                                    level="TRACE"
                                    @follow="forwardEvent('follow', $event)"
                                    :targetFlow="executionsStore.flow"
                                    :showLogs="taskTypeByTaskRunId[item.id] !== 'io.kestra.plugin.core.flow.ForEachItem' && taskTypeByTaskRunId[item.id] !== 'io.kestra.core.tasks.flows.ForEachItem'"
                                    class="mh-100 mx-3"
                                />
                            </div>
                        </div>
                    </DynamicScrollerItem>
                </template>
            </DynamicScroller>
        </template>
    </el-card>
</template>
<script>
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import {State} from "@kestra-io/ui-libs"
    import Duration from "../layout/Duration.vue";
    import Utils from "../../utils/utils";
    import FlowUtils from "../../utils/flowUtils";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import {useBreakpoints, breakpointsElement} from "@vueuse/core";

    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import Warning from "vue-material-design-icons/Alert.vue";
    import ExecutionPending from "./ExecutionPending.vue";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";

    const ts = date => new Date(date).getTime();
    const TASKRUN_THRESHOLD = 50;
    export default {
        components: {
            DynamicScroller,
            Warning,
            DynamicScrollerItem,
            TaskRunDetails,
            Duration,
            ChevronRight,
            ChevronDown,
            ExecutionPending
        },
        setup() {
            const verticalLayout = useBreakpoints(breakpointsElement).smallerOrEqual("sm");
            return {
                verticalLayout
            };
        },
        data() {
            return {
                colors: State.colorClass(),
                series: [],
                dates: [],
                duration: undefined,
                selectedTaskRuns: [],
                regularPaintingInterval: undefined,
                taskTypesToExclude: [
                    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemSplit",
                    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemMergeOutputs",
                    "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable",
                    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit",
                    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs",
                    "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable"
                ]
            };
        },
        watch: {
            execution: {
                handler(newValue) {
                    if (!State.isRunning(newValue.state?.current)) {
                        clearInterval(this.regularPaintingInterval);
                        this.regularPaintingInterval = undefined;
                        this.compute();
                    } else if (this.regularPaintingInterval === undefined) {
                        this.regularPaintingInterval = setInterval(this.compute, this.taskRunsCount < TASKRUN_THRESHOLD ? 40 : 500);
                    }
                },
                immediate: true
            },
            forEachItemsTaskRunIds: {
                handler(newValue, oldValue) {
                    if (newValue.length > 0) {
                        const newEntriesAmount = newValue.length - (oldValue?.length ?? 0);
                        for (let i = newValue.length - newEntriesAmount; i < newValue.length; i++) {
                            this.selectedTaskRuns.push(newValue[i].id);
                        }
                    }
                },
                immediate: true
            }
        },
        computed: {
            ...mapStores(useExecutionsStore),
            execution(){
                return this.executionsStore.execution
            },
            taskRunsCount() {
                return this.execution && this.execution.taskRunList ? this.execution.taskRunList.length : 0
            },
            taskTypeByTaskRun() {
                return this.series.map(serie => [serie.task, this.taskType(serie.task)]);
            },
            taskTypeByTaskRunId() {
                return Object.fromEntries(this.taskTypeByTaskRun.map(([taskRun, taskType]) => [taskRun.id, taskType]));
            },
            forEachItemsTaskRunIds() {
                return this.taskTypeByTaskRun.filter(([, taskType]) => taskType === "io.kestra.plugin.core.flow.ForEachItem" || taskType === "io.kestra.core.tasks.flows.ForEachItem").map(([taskRunId]) => taskRunId);
            },
            filteredSeries() {
                return this.series
                    .filter(serie =>
                        !this.taskTypesToExclude.includes(this.taskTypeByTaskRunId[serie.task.id])
                    );
            },
            start() {
                return this.execution ? ts(this.execution.state.histories[0].date) : 0;
            },
            tasks () {
                const rootTasks = []
                const childTasks = []
                const sortedTasks = []
                const tasksById = {}
                for (let task of (this.execution.taskRunList || [])) {
                    const taskWrapper = {task, depth: task.parentTaskRunId ? undefined : 0}
                    if (task.parentTaskRunId) {
                        childTasks.push(taskWrapper)
                    } else {
                        rootTasks.push(taskWrapper)
                    }
                    tasksById[task.id] = taskWrapper
                }

                for (let i = 0; i < childTasks.length; i++) {
                    const taskWrapper = childTasks[i];
                    const parentTask = tasksById[taskWrapper.task.parentTaskRunId]
                    if (parentTask) {
                        taskWrapper.depth = parentTask.depth + 1
                        tasksById[taskWrapper.task.id] = taskWrapper
                        if (!parentTask.children) {
                            parentTask.children = []
                        }
                        parentTask.children.push(taskWrapper)
                    }
                }

                const nodeStart = node => ts(node.task.state.histories[0].date)
                const childrenSort = nodes => {
                    nodes.sort((n1,n2) => {
                        return nodeStart(n1) > nodeStart(n2) ? 1 : -1
                    })
                    for (let node of nodes) {
                        sortedTasks.push(node)
                        if (node.children) {
                            childrenSort(node.children)
                        }
                    }
                }
                childrenSort(rootTasks)
                return sortedTasks
            },
            isExecutionStarted() {
                return this.execution?.state?.current && !["CREATED", "QUEUED"].includes(this.execution.state.current);
            },
            hasValidDate() {
                return isFinite(this.delta());
            },
            startTime() {
                if (!this.execution) return "";
                return this.$moment(this.execution.state.histories[0].date).format("HH:mm:ss");
            },
            endTime() {
                if (!this.execution) return "";
                const endDate = State.isRunning(this.execution.state.current) 
                    ? new Date() 
                    : new Date(this.stop());
                return this.$moment(endDate).format("HH:mm:ss");
            },
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            compute() {
                this.computeSeries();
                this.computeDates();
            },
            delta() {
                return this.stop() - this.start;
            },
            stop() {
                if (!this.execution || State.isRunning(this.execution.state.current)) {
                    return +new Date();
                }

                return Math.max(...(this.execution.taskRunList || []).map(r => {
                    let lastIndex = r.state.histories.length - 1
                    return ts(r.state.histories[lastIndex].date)
                }));
            },
            computeSeries() {
                if (!this.execution) {
                    return;
                }

                const series = [];
                const executionDelta = this.delta();
                const taskMap = {};
                
                for (let taskWrapper of this.tasks) {
                    let task = taskWrapper.task
                    let stopTs;
                    if (State.isRunning(task.state.current)) {
                        stopTs = ts(new Date());
                    } else {
                        const lastIndex = task.state.histories.length - 1;
                        stopTs = ts(task.state.histories[lastIndex].date);
                    }

                    const startTs = ts(task.state.histories[0].date);

                    const runningState = task.state.histories.filter(r => r.state === State.RUNNING);
                    const left = runningState.length > 0 ? ((ts(runningState[0].date) - startTs) / (stopTs - startTs) * 100) : 0;

                    const start = startTs - this.start;
                    let stop = stopTs - this.start - start;

                    const delta = stopTs - startTs;
                    const duration = this.$moment.duration(delta);

                    let tooltip = `${this.$t("duration")} : ${Utils.humanDuration(duration)}`

                    if (runningState.length > 0) {
                        tooltip += `\n${this.$t("queued duration")} : ${Utils.humanDuration((ts(runningState[0].date) - startTs) / 1000)}`;
                        tooltip += `\n${this.$t("running duration")} : ${Utils.humanDuration((stopTs - ts(runningState[0].date)) / 1000)}`;
                    }

                    let width = (stop / executionDelta) * 100
                    if (State.isRunning(task.state.current)) {
                        width = ((this.stop() - startTs) / executionDelta) * 100
                    }

                    let startPercent = (start / executionDelta) * 100;
                    let parentEndPercent = undefined;
                    
                    if (task.parentTaskRunId && taskMap[task.parentTaskRunId]) {
                        const parent = taskMap[task.parentTaskRunId];
                        parentEndPercent = parent.start + parent.width;
                    }

                    const seriesItem = {
                        id: task.id,
                        name: task.taskId,
                        start: startPercent,
                        width,
                        left: left,
                        tooltip,
                        color: this.colors[task.state.current],
                        running: State.isRunning(task.state.current),
                        task,
                        flowId: task.flowId,
                        namespace: task.namespace,
                        executionId: task.outputs && task.outputs.executionId,
                        attempts: task.attempts ? task.attempts.length : 1,
                        depth: taskWrapper.depth,
                        parentEndPercent: parentEndPercent
                    };
                    
                    taskMap[task.id] = seriesItem;
                    series.push(seriesItem);
                }
                this.series = series;
            },
            computeDates() {
                const ticks = 5;
                const date = ts => this.$moment(ts).format("h:mm:ss");
                const start = this.start;
                const delta = this.delta() / ticks;
                const dates = [];
                for (let i = 0; i < ticks; i++) {
                    dates.push(date(start + i * delta));
                }
                this.dates = dates;
            },
            onTaskSelect(taskRunId) {
                if(this.selectedTaskRuns.includes(taskRunId)) {
                    this.selectedTaskRuns = this.selectedTaskRuns.filter(id => id !== taskRunId);
                    return
                }

                this.selectedTaskRuns.push(taskRunId);
            },
            taskType(taskRun) {
                const task = FlowUtils.findTaskById(this.executionsStore.flow, taskRun.taskId);
                return task?.type;
            }
        },
        unmounted() {
            clearInterval(this.regularPaintingInterval);
        }
    };
</script>

<style scoped lang="scss">
    .el-card {
        padding: 0;

        :deep(.el-card__header) {
            padding: 0;
            font-size: var(--font-size-sm);
            background-color: var(--bs-gray-200);

            > div {
                > * {
                    padding: .5rem;
                    flex: 1;
                }

                > .th {
                    background-color: var(--bs-gray-100-darken-5);
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
                        font-size: var(--font-size-sm);
                        color: var(--ks-content-primary);
                    }
                }
            }
        }

        :deep(.el-card__body) {
            padding: 0;

            .vue-recycle-scroller {
                max-height: calc(100vh - 223px);

                &::-webkit-scrollbar {
                    width: 5px;
                }

                &::-webkit-scrollbar-track {
                    background: var(--ks-background-body);
                }

                &::-webkit-scrollbar-thumb {
                    background: var(--ks-border-primary);
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

                .el-tooltip__trigger {
                    flex: 1;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;

                    small {
                        margin-left: 5px;
                        font-family: var(--bs-font-monospace);
                        font-size: var(--font-size-xs);
                    }

                    code {
                        font-size: var(--font-size-sm);
                        color: var(--ks-content-primary);
                    }
                }

                .task-name {
                    flex: 1;
                    min-width: 100px;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;

                    code {
                        font-size: var(--font-size-sm);
                        color: var(--ks-content-primary);
                    }

                    small {
                        margin-left: 5px;
                        font-family: var(--bs-font-monospace);
                        font-size: var(--font-size-xs);
                    }
                }

                .attempt_warn{
                    color: var(--el-color-warning);
                    vertical-align: middle;
                }

                .task-progress {
                    position: relative;
                    transition: all 0.3s;
                    min-width: 5px;

                    .progress {
                        height: 25px;
                        border-radius: var(--bs-border-radius-sm);
                        background-color: var(--bs-gray-200);
                        cursor: pointer;

                        .progress-bar {
                            position: absolute;
                            height: 25px;
                            transition: none;
                        }
                    }
                }
            }
        }
    }

    .no-border {
        border: none !important;
    }

    // To Separate through Line
    :deep(.vue-recycle-scroller__item-view) {
        border-bottom: 1px solid var(--ks-border-primary);
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
            border-radius: var(--bs-border-radius-lg);
        }
    }
</style>