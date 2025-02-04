<template>
    <el-card id="gantt" shadow="never" v-if="execution && flow">
        <template #header>
            <div class="d-flex">
                <duration class="th text-end" :histories="execution.state.histories" />
                <span class="text-end" v-for="(date, i) in dates" :key="i">
                    {{ date }}
                </span>
            </div>
        </template>
        <template #default>
            <DynamicScroller
                :items="filteredSeries"
                :min-item-size="40"
                key-field="id"
                :buffer="0"
                :update-interval="0"
            >
                <template #default="{item, index, active}">
                    <DynamicScrollerItem
                        :item="item"
                        :active="active"
                        :data-index="index"
                        :size-dependencies="[selectedTaskRuns]"
                    >
                        <div class="d-flex flex-column">
                            <div class="gantt-row d-flex cursor-icon" @click="onTaskSelect(item.id)">
                                <div class="d-inline-flex">
                                    <ChevronRight v-if="!selectedTaskRuns.includes(item.id)" />
                                    <ChevronDown v-else />
                                </div>
                                <el-tooltip placement="top-start" :persistent="false" transition="" :hide-after="0" effect="light">
                                    <template #content>
                                        <code>{{ item.name }}</code>
                                        <small v-if="item.task && item.task.value"><br>{{ item.task.value }}</small>
                                    </template>
                                    <span>
                                        <code>{{ item.name }}</code>
                                        <small v-if="item.task && item.task.value"> {{ item.task.value }}</small>
                                    </span>
                                </el-tooltip>
                                <div>
                                    <el-tooltip placement="right" :persistent="false" :hide-after="0" effect="light">
                                        <template #content>
                                            <span>This task has {{ item.attempts }} attempts.</span>
                                        </template>
                                        <Warning
                                            v-if="item.attempts > 1"
                                            class="attempt_warn me-3"
                                        />
                                    </el-tooltip>
                                </div>
                                <div :style="'width: ' + (100 / (dates.length + 1)) * dates.length + '%'">
                                    <el-tooltip placement="top" :persistent="false" transition="" :hide-after="0" effect="light">
                                        <template #content>
                                            <span style="white-space: pre-wrap;">
                                                {{ item.tooltip }}
                                            </span>
                                        </template>
                                        <div
                                            :style="{left: item.start + '%', width: item.width + '%'}"
                                            class="task-progress"
                                        >
                                            <div class="progress">
                                                <div
                                                    class="progress-bar"
                                                    :style="{left: item.left + '%', width: (100-item.left) + '%'}"
                                                    :class="'bg-' + item.color + (item.running ? ' progress-bar-striped progress-bar-animated' : '')"
                                                    role="progressbar"
                                                />
                                            </div>
                                        </div>
                                    </el-tooltip>
                                </div>
                            </div>
                            <div v-if="selectedTaskRuns.includes(item.id)" class="p-2">
                                <task-run-details
                                    :task-run-id="item.id"
                                    :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                                    level="TRACE"
                                    @follow="forwardEvent('follow', $event)"
                                    :target-execution="execution"
                                    :target-flow="flow"
                                    :show-logs="taskTypeByTaskRunId[item.id] !== 'io.kestra.plugin.core.flow.ForEachItem' && taskTypeByTaskRunId[item.id] !== 'io.kestra.core.tasks.flows.ForEachItem'"
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
    import {mapState} from "vuex";
    import {State} from "@kestra-io/ui-libs"
    import Duration from "../layout/Duration.vue";
    import Utils from "../../utils/utils";
    import FlowUtils from "../../utils/flowUtils";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import Warning from "vue-material-design-icons/Alert.vue";

    const ts = date => new Date(date).getTime();
    const TASKRUN_THRESHOLD = 50;
    export default {
        components: {DynamicScroller,Warning, DynamicScrollerItem, TaskRunDetails, Duration, ChevronRight, ChevronDown},
        data() {
            return {
                colors: State.colorClass(),
                series: [],
                dates: [],
                duration: undefined,
                selectedTaskRuns: [],
                regularPaintingInterval: undefined,
                taskTypesToExclude: ["io.kestra.plugin.core.flow.ForEachItem$ForEachItemSplit", "io.kestra.plugin.core.flow.ForEachItem$ForEachItemMergeOutputs", "io.kestra.plugin.core.flow.ForEachItem$ForEachItemExecutable", "io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit", "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs", "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable"]
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
            ...mapState("execution", ["flow", "execution"]),
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
                    const taskWrapper = {task}
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
                        sortedTasks.push(node.task)
                        if (node.children) {
                            childrenSort(node.children)
                        }
                    }
                }
                childrenSort(rootTasks)
                return sortedTasks
            }
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
                const executionDelta = this.delta(); //caching this value matters
                for (let task of this.tasks) {
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
                        width = ((this.stop() - startTs) / executionDelta) * 100 //(stop / executionDelta) * 100
                    }

                    series.push({
                        id: task.id,
                        name: task.taskId,
                        start: (start / executionDelta) * 100,
                        width,
                        left: left,
                        tooltip,
                        color: this.colors[task.state.current],
                        running: State.isRunning(task.state.current),
                        task,
                        flowId: task.flowId,
                        namespace: task.namespace,
                        executionId: task.outputs && task.outputs.executionId,
                        attempts: task.attempts ? task.attempts.length : 1
                    });
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
                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                return task?.type;
            }
        },
        unmounted() {
            clearInterval(this.regularPaintingInterval);
        }
    };
</script>

<style lang="scss" scoped>
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
                    background: var(--bs-gray-500);
                }

                &::-webkit-scrollbar-thumb {
                    background: var(--ks-button-background-primary);
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
                        font-size: var(--font-size-xs);
                        color: var(--ks-content-primary);
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