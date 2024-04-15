<template>
    <el-card shadow="never" v-if="execution && flow">
        <table>
            <thead>
                <tr>
                    <th>
                        <duration :histories="execution.state.histories" />
                    </th>
                    <td v-for="(date, i) in dates" :key="i">
                        {{ date }}
                    </td>
                </tr>
            </thead>
            <tbody v-for="serie in filteredSeries" :key="serie.id">
                <tr>
                    <th dir="rtl">
                        <el-tooltip placement="top-start" :persistent="false" transition="" :hide-after="0">
                            <template #content>
                                <code>{{ serie.name }}</code>
                                <small v-if="serie.task && serie.task.value"><br>{{ serie.task.value }}</small>
                            </template>
                            <span>
                                <code>{{ serie.name }}</code>
                                <small v-if="serie.task && serie.task.value"> {{ serie.task.value }}</small>
                            </span>
                        </el-tooltip>
                    </th>
                    <td :colspan="dates.length" @click="onTaskSelect(serie.task)" class="cursor-pointer">
                        <el-tooltip placement="top" :persistent="false" transition="" :hide-after="0">
                            <template #content>
                                <span style="white-space: pre-wrap;">
                                    {{ serie.tooltip }}
                                </span>
                            </template>
                            <div
                                :style="{left: serie.start + '%', width: serie.width + '%'}"
                                class="task-progress"
                                @click="onTaskSelect(serie.task)"
                            >
                                <div class="progress">
                                    <div
                                        class="progress-bar"
                                        :style="{left: serie.left + '%', width: (100-serie.left) + '%'}"
                                        :class="'bg-' + serie.color + (serie.running ? ' progress-bar-striped progress-bar-animated' : '')"
                                        role="progressbar"
                                    />
                                </div>
                            </div>
                        </el-tooltip>
                    </td>
                </tr>
                <tr v-if="selectedTaskRuns.includes(serie.id)">
                    <td :colspan="dates.length + 1" class="p-0 pb-2">
                        <task-run-details
                            :task-run-id="serie.id"
                            :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                            level="TRACE"
                            @follow="forwardEvent('follow', $event)"
                            :target-execution="execution"
                            :target-flow="flow"
                            :show-logs="taskTypeByTaskRunId[serie.task.id] !== 'io.kestra.core.tasks.flows.ForEachItem'"
                        />
                    </td>
                </tr>
            </tbody>
        </table>
    </el-card>
</template>
<script>
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import {mapState} from "vuex";
    import State from "../../utils/state";
    import Duration from "../layout/Duration.vue";
    import Utils from "../../utils/utils";
    import FlowUtils from "../../utils/flowUtils";

    const ts = date => new Date(date).getTime();
    const TASKRUN_THRESHOLD = 50
    export default {
        components: {TaskRunDetails, Duration},
        data() {
            return {
                colors: State.colorClass(),
                series: [],
                realTime: true,
                dates: [],
                duration: undefined,
                usePartialSerie: true,
                selectedTaskRuns: [],
                taskTypesToExclude: ["io.kestra.core.tasks.flows.ForEachItem$ForEachItemSplit", "io.kestra.core.tasks.flows.ForEachItem$ForEachItemMergeOutputs", "io.kestra.core.tasks.flows.ForEachItem$ForEachItemExecutable"]
            };
        },
        watch: {
            execution(newValue, oldValue) {
                if (oldValue.id !== newValue.id) {
                    this.realTime = true;
                    this.paint();
                } else {
                    this.compute()
                }
            },
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.compute()
                }
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
        mounted() {
            this.paint();
        },
        computed: {
            ...mapState("execution", ["execution"]),
            ...mapState("flow", ["flow"]),
            taskRunsCount() {
                return this.execution && this.execution.taskRunList ? this.execution.taskRunList.length : 0
            },
            partialSeries() {
                return (this.series || []).slice(0, this.usePartialSerie ? TASKRUN_THRESHOLD : this.taskRunsCount)
            },
            taskTypeByTaskRun() {
                return this.partialSeries.map(serie => [serie.task, this.taskType(serie.task)]);
            },
            taskTypeByTaskRunId() {
                return Object.fromEntries(this.taskTypeByTaskRun.map(([taskRun, taskType]) => [taskRun.id, taskType]));
            },
            forEachItemsTaskRunIds() {
                return this.taskTypeByTaskRun.filter(([, taskType]) => taskType === "io.kestra.core.tasks.flows.ForEachItem").map(([taskRunId]) => taskRunId);
            },
            filteredSeries() {
                return this.partialSeries
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
            paint() {
                const repaint = () => {
                    this.compute()
                    if (this.realTime) {
                        const delay = this.taskRunsCount < TASKRUN_THRESHOLD ? 40 : 500
                        setTimeout(repaint, delay);
                    }
                }
                setTimeout(repaint);
                setTimeout(() => {
                    this.usePartialSerie = false
                }, 500);
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

                if (!State.isRunning(this.execution.state.current)) {
                    this.stopRealTime();
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
                        executionId: task.outputs && task.outputs.executionId
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
            onTaskSelect(taskRun) {
                if(this.selectedTaskRuns.includes(taskRun.id)) {
                    this.selectedTaskRuns = this.selectedTaskRuns.filter(id => id !== taskRun.id);
                    return
                }

                this.selectedTaskRuns.push(taskRun.id);
            },
            stopRealTime() {
                this.realTime = false
            },
            taskType(taskRun) {
                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                return task?.type;
            }
        },
        unmounted() {
            this.stopRealTime();
        }
    };
</script>
<style lang="scss" scoped>
    .el-card {
        :deep(.el-card__body) {
            padding: 0;
        }

    }

    .cursor-pointer {
        cursor: pointer;
    }

    table {
        table-layout: fixed;
        width: 100%;
        color: var(--bs-body-color);

        & th, td {
            padding: calc(var(--spacer) / 2);
        }

        tr:last-child th, tr:last-child td {
            border-bottom: 0;
        }

        thead th, thead td {
            text-align: right;
        }

        thead {
            font-size: var(--font-size-sm);
            background-color: var(--bs-gray-200);

            th {
                background-color: var(--bs-gray-100-darken-5);
            }
        }
        th {
            min-width: 150px;
            max-width: 200px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;

            small {
                margin-left: 5px;
            }

            small {
                color: var(--bs-gray-600);
                font-family: var(--bs-font-monospace);
                font-size: var(--font-size-xs)
            }
        }

        code {
            font-size: 0.7rem;
        }

        tbody {
            th {
                code {
                    font-weight: normal;
                }
            }

            td {
                position: relative;

                .task-progress {
                    position: relative;
                    transition: all 0.3s;
                    min-width: 5px;

                    .progress {
                        height: 21px;
                        border-radius: var(--bs-border-radius-sm);
                        position: relative;
                        cursor: pointer;
                        background-color: var(--bs-gray-200);

                        .progress-bar {
                            position: absolute;
                            height: 21px;
                            transition: none;
                        }
                    }
                }
            }

            tr:nth-child(2) td {
                padding-bottom: 0 !important;
            }
        }
    }

    :deep(.log-wrapper .attempt-wrapper) {
        margin-bottom: 0;
        border-radius: 0;
        border: 0;
        border-top: 1px solid var(--bs-gray-600);
        border-bottom: 1px solid var(--bs-gray-600);

        tbody:last-child & {
            border-bottom: 0;
        }

        .attempt-header {
            padding: calc(var(--spacer) / 2);
        }

        .line {
            padding-left: calc(var(--spacer) / 2);
        }
    }

</style>
