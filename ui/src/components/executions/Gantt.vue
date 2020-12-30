<template>
    <div v-if="execution">
        <div class="table-responsive">
            <table class="table table-sm table-bordered">
                <thead>
                    <tr class="bg-light">
                        <th>{{ duration }}</th>
                        <td v-for="(date, i) in dates" :key="i">
                            {{ date }}
                        </td>
                    </tr>
                </thead>
                <tbody v-for="currentTaskRun in partialSeries" :key="currentTaskRun.id">
                    <tr>
                        <th :id="`task-title-wrapper-${currentTaskRun.id}`">
                            <sub-flow-link class="sub-flow" v-if="currentTaskRun.executionId" tab-execution="gantt" :execution-id="currentTaskRun.executionId" :namespace="currentTaskRun.namespace" :flow-id="currentTaskRun.flowId" />
                            <code>{{ currentTaskRun.name }}</code>
                            <small v-if="currentTaskRun.task && currentTaskRun.task.value"> {{ currentTaskRun.task.value }}</small>
                            <b-tooltip
                                placement="right"
                                :target="`task-title-wrapper-${currentTaskRun.id}`"
                            >
                                <code>{{ currentTaskRun.name }}</code>
                                <span v-if="currentTaskRun.task && currentTaskRun.task.value"><br>{{ currentTaskRun.task.value }}</span>
                            </b-tooltip>
                        </th>
                        <td :colspan="dates.length">
                            <b-tooltip
                                :target="`task-progress-${currentTaskRun.id}`"
                                placement="left"
                            >
                                <span v-html="currentTaskRun.tooltip" />
                            </b-tooltip>
                            <div
                                :style="{left: Math.max(1, (currentTaskRun.start - 1)) + '%', width: currentTaskRun.width - 1 + '%'}"
                                class="task-progress"
                                @click="onTaskSelect(currentTaskRun.task)"
                                :id="`task-progress-${currentTaskRun.id}`"
                            >
                                <div class="progress">
                                    <div
                                        class="progress-bar"
                                        :style="{left: currentTaskRun.left + '%', width: (100-currentTaskRun.left) + '%'}"
                                        :class="'bg-' + currentTaskRun.color + (currentTaskRun.running ? ' progress-bar-striped' : '')"
                                        role="progressbar"
                                    />
                                </div>
                            </div>
                        </td>
                    </tr>
                    <tr v-if="taskRun && taskRun.id === currentTaskRun.id">
                        <td :colspan="dates.length + 1">
                            <log-list :task-run-id="taskRun.id" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" level="TRACE" />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>
<script>
    import LogList from "../logs/LogList";
    import {mapState} from "vuex";
    import humanizeDuration from "humanize-duration";
    import State from "../../utils/state";
    import SubFlowLink from "../flows/SubFlowLink"

    const ts = date => new Date(date).getTime();
    const TASKRUN_THRESHOLD = 50
    export default {
        components: {LogList, SubFlowLink},
        data() {
            return {
                colors: State.colorClass(),
                series: [],
                realTime: true,
                dates: [],
                duration: undefined,
                usePartialSerie: true,
            };
        },
        watch: {
            execution() {
                this.compute()
            },
            $route() {
                this.compute()
            }
        },
        mounted() {
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
        computed: {
            ...mapState("execution", ["taskRun", "execution"]),
            taskRunsCount() {
                return this.execution && this.execution.taskRunList ? this.execution.taskRunList.length : 0
            },
            partialSeries() {
                return (this.series || []).slice(0, this.usePartialSerie ? TASKRUN_THRESHOLD : this.taskRunsCount)
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
                while (childTasks.length) {
                    const taskWrapper = childTasks.pop()
                    const parentTask = tasksById[taskWrapper.task.parentTaskRunId]
                    if (parentTask) {
                        tasksById[taskWrapper.task.id] = taskWrapper
                        if (!parentTask.children) {
                            parentTask.children = []
                        }
                        parentTask.children.push(taskWrapper)
                    } else {
                        childTasks.unshift(taskWrapper)
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
            compute() {
                this.computeSeries();
                this.computeDates();
                this.computeDuration();
            },
            delta() {
                return this.stop() - this.start;
            },
            stop() {
                if (!this.execution || State.isRunning(this.execution.state.current)) {
                    return +new Date();
                }

                return Math.max(...this.execution.taskRunList.map(r => {
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

                    let tooltip = `${this.$t("duration")} : ${humanizeDuration(duration)}`

                    if (runningState.length > 0) {
                        tooltip += `<br />${this.$t("queued duration")} : ${humanizeDuration(ts(runningState[0].date) - startTs)}`;
                        tooltip += `<br />${this.$t("running duration")} : ${humanizeDuration(stopTs - ts(runningState[0].date))}`;
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
            computeDuration() {
                this.duration = humanizeDuration(this.delta());
            },
            onTaskSelect(taskRun) {
                taskRun = this.taskRun && this.taskRun.id === taskRun.id ? undefined : taskRun;
                this.$store.commit("execution/setTaskRun", taskRun);
            },
            stopRealTime() {
                this.realTime = false
            }
        },
        destroyed() {
            this.stopRealTime();
        }
    };
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

table {
    & th, td {
        border-color: $table-border-color;
    }

    thead th, thead td {
        text-align: right;
    }

    tbody {
        border-top: 0;
    }

    thead {
        font-size: $font-size-sm;

        th {
            width: 150px;
        }
    }
    th {
        max-width: 150px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
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
                position: absolute;
                transition: all 0.3s;
                min-width: 5px;

                .progress {
                    height: 21px;
                    border-radius: 2px;
                    position: relative;
                    cursor: pointer;

                    .progress-bar {
                        position: absolute;
                        height: 21px;
                        transition: none;
                    }
                }
            }
        }
    }
}

/deep/ .log-wrapper .attempt-wrapper {
    margin-bottom: 0;
}

.sub-flow {
    display: inline;
    margin-right: 5px;
    /deep/ button {
        font-size: 0.8em;
        height: 20px;
        width: 20px;
        padding: 0px;
        border-radius: 5px;
    }
}

</style>
