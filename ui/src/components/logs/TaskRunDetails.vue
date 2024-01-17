<template>
    <div v-if="followedExecution" class="log-wrapper">
        <div v-for="currentTaskRun in currentTaskRuns" :key="currentTaskRun.id">
            <template
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
            >
                <el-card class="attempt-wrapper">
                    <task-run-line
                        :current-task-run="currentTaskRun"
                        :followed-execution="followedExecution"
                        :flow="flow"
                        :forced-attempt-number="forcedAttemptNumber"
                        :task-run-id="taskRunId"
                        @toggle-show-attempt="toggleShowAttempt"
                        @swap-displayed-attempt="swapDisplayedAttempt"
                        :selected-attempt-number-by-task-run-id="selectedAttemptNumberByTaskRunId"
                        :shown-attempts-uid="shownAttemptsUid"
                        :logs="logs"
                    />
                    <for-each-status
                        v-if="shouldDisplayProgressBar(currentTaskRun) && showProgressBar"
                        :execution-id="currentTaskRun.executionId"
                        :subflows-status="currentTaskRun?.outputs?.iterations"
                        :max="currentTaskRun.outputs.numberOfBatches"
                    />
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun)"
                        :items="logsWithIndexByAttemptUid[attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])] ?? []"
                        :min-item-size="50"
                        key-field="index"
                        class="log-lines"
                        :ref="attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])"
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, index, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :size-dependencies="[item.message, item.image]"
                                :data-index="index"
                            >
                                <log-line
                                    :level="level"
                                    :log="item"
                                    :exclude-metas="excludeMetas"
                                    v-if="filter === '' || item.message?.toLowerCase().includes(filter)"
                                />
                                <task-run-details
                                    v-if="!taskRunId && isSubflow(currentTaskRun) && currentTaskRun.outputs?.executionId"
                                    ref="subflows-logs"
                                    :level="level"
                                    :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                                    :filter="filter"
                                    :allow-auto-expand-subflows="false"
                                    :target-execution-id="currentTaskRun.outputs.executionId"
                                    :class="$el.classList.contains('even') ? '' : 'even'"
                                    :show-progress-bar="showProgressBar"
                                    :show-logs="showLogs"
                                />
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </el-card>
            </template>
        </div>
    </div>
</template>

<script>
    import LogLine from "./LogLine.vue";
    import State from "../../utils/state";
    import _xor from "lodash/xor";
    import _groupBy from "lodash/groupBy";
    import moment from "moment";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {logDisplayTypes} from "../../utils/constants";
    import Download from "vue-material-design-icons/Download.vue";
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import {mapState} from "vuex";
    import ForEachStatus from "../executions/ForEachStatus.vue";
    import TaskRunLine from "../executions/TaskRunLine.vue";
    import FlowUtils from "../../utils/flowUtils";

    export default {
        name: "TaskRunDetails",
        components: {
            TaskRunLine,
            ForEachStatus,
            LogLine,
            DynamicScroller,
            DynamicScrollerItem,
        },
        emits: ["opened-taskruns-count", "follow", "reset-expand-collapse-all-switch"],
        props: {
            level: {
                type: String,
                default: "INFO",
            },
            filter: {
                type: String,
                default: "",
            },
            taskRunId: {
                type: String,
                default: undefined,
            },
            excludeMetas: {
                type: Array,
                default: () => [],
            },
            forcedAttemptNumber: {
                type: Number,
                default: undefined
            },
            // allows to pass directly a raw execution (since it is already fetched by parent component)
            targetExecution: {
                type: Object,
                required: false
            },
            // allows to fetch the execution at startup
            targetExecutionId: {
                type: String,
                default: undefined
            },
            // allows to pass directly a flow source (since it is already fetched by parent component)
            targetFlow: {
                type: Object,
                default: undefined
            },
            allowAutoExpandSubflows: {
                type: Boolean,
                default: true
            },
            showProgressBar: {
                type: Boolean,
                default: true
            },
            showLogs: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                showOutputs: {},
                showMetrics: {},
                fullscreen: false,
                followed: false,
                shownAttemptsUid: [],
                logs: [],
                timer: undefined,
                timeout: undefined,
                selectedAttemptNumberByTaskRunId: {},
                followedExecution: undefined,
                executionSSE: undefined,
                logsSSE: undefined,
                flow: undefined,
                logsBuffer: []
            };
        },
        watch: {
            "shownAttemptsUid.length": function (openedTaskrunsCount) {
                this.$emit("opened-taskruns-count", openedTaskrunsCount);
            },
            level: function () {
                this.logs = [];
                this.loadLogs(this.followedExecution.id);
            },
            execution: function () {
                if (this.execution && this.execution.state.current !== State.RUNNING && this.execution.state.current !== State.PAUSED) {
                    this.closeSSE();
                }
            },
            currentTaskRuns: {
                handler(taskRuns) {
                    // by default we preselect the last attempt for each task run
                    this.selectedAttemptNumberByTaskRunId = Object.fromEntries(taskRuns.map(taskRun => [taskRun.id, this.forcedAttemptNumber ?? this.attempts(taskRun).length - 1]));
                },
                immediate: true
            },
            targetExecution: {
                handler: function (newExecution) {
                    if (newExecution) {
                        this.followedExecution = newExecution;
                    }
                },
                immediate: true
            },
            targetFlow: {
                handler: function (flowSource) {
                    if (flowSource) {
                        this.flow = flowSource;
                    }
                },
                immediate: true
            },
            followedExecution: {
                handler: async function (newExecution) {
                    if (!newExecution) {
                        return;
                    }

                    if (!this.targetFlow) {
                        this.flow = await this.$store.dispatch(
                            "flow/loadFlow",
                            {
                                namespace: newExecution.namespace,
                                id: newExecution.flowId,
                                revision: newExecution.flowRevision,
                                store: false
                            }
                        );
                    }

                    if (![State.RUNNING, State.PAUSED].includes(this.followedExecution.state.current)) {
                        this.executionSSE?.close();
                        // wait a bit to make sure we don't miss logs as log indexer is asynchronous
                        setTimeout(() => {
                            this.logsSSE?.close();
                        }, 2000);

                        if (!this.logsSSE) {
                            this.loadLogs(newExecution.id);
                        }

                        return;
                    }

                    // running or paused
                    if (!this.logsSSE) {
                        this.followLogs(newExecution.id);
                    }
                },
                immediate: true
            }
        },
        mounted() {
            if (this.targetExecutionId) {
                this.followExecution(this.targetExecutionId);
            }

            this.autoExpandBasedOnSettings();
        },
        computed: {
            ...mapState("plugin", ["icons"]),
            Download() {
                return Download
            },
            currentTaskRuns() {
                return this.followedExecution?.taskRunList?.filter(tr => this.taskRunId ? tr.id === this.taskRunId : true) ?? [];
            },
            params() {
                let params = {minLevel: this.level};

                if (this.taskRunId) {
                    params.taskRunId = this.taskRunId;

                    if (this.forcedAttemptNumber) {
                        params.attempt = this.forcedAttemptNumber;
                    }
                }

                return params
            },
            taskRunById() {
                return Object.fromEntries(this.currentTaskRuns.map(taskRun => [taskRun.id, taskRun]));
            },
            logsWithIndexByAttemptUid() {
                const indexedLogs = this.logs
                    .filter(logLine => this.filter === "" || logLine?.message.toLowerCase().includes(this.filter) || this.isSubflow(this.taskRunById[logLine.taskRunId]))
                    .map((logLine, index) => ({...logLine, index}));

                return _groupBy(indexedLogs, indexedLog => this.attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber));
            },
            autoExpandTaskrunStates() {
                switch (localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT) {
                    case logDisplayTypes.ERROR:
                        return [State.FAILED, State.RUNNING, State.PAUSED]
                    case logDisplayTypes.ALL:
                        return State.arrayAllStates().map(s => s.name)
                    case logDisplayTypes.HIDDEN:
                        return []
                    default:
                        return State.arrayAllStates().map(s => s.name)
                }
            }
        },
        methods: {
            toggleExpandCollapseAll() {
                this.shownAttemptsUid.length === 0 ? this.expandAll() : this.collapseAll();
            },
            autoExpandBasedOnSettings() {
                if (this.autoExpandTaskrunStates.length === 0) {
                    return;
                }

                if (this.followedExecution === undefined) {
                    setTimeout(() => this.autoExpandBasedOnSettings(), 50);
                    return;
                }
                this.currentTaskRuns.forEach((taskRun) => {
                    if (this.isSubflow(taskRun) && !this.allowAutoExpandSubflows) {
                        return;
                    }

                    if (this.taskRunId === taskRun.id || this.autoExpandTaskrunStates.includes(taskRun.state.current)) {
                        this.toggleShowAttempt(this.attemptUid(taskRun.id, this.selectedAttemptNumberByTaskRunId[taskRun.id]));
                    }
                });
            },
            shouldDisplayProgressBar(taskRun) {
                return this.showProgressBar &&
                    this.taskType(taskRun) === "io.kestra.core.tasks.flows.ForEachItem"
            },
            shouldDisplayLogs(taskRun) {
                return (this.taskRunId ||
                    (this.shownAttemptsUid.includes(this.attemptUid(taskRun.id, this.selectedAttemptNumberByTaskRunId[taskRun.id])) &&
                        this.logsWithIndexByAttemptUid[this.attemptUid(taskRun.id, this.selectedAttemptNumberByTaskRunId[taskRun.id])])) &&
                    this.showLogs
            },
            followExecution(executionId) {
                this.$store
                    .dispatch("execution/followExecution", {id: executionId})
                    .then(sse => {
                        this.executionSSE = sse;
                        this.executionSSE.onmessage = async (event) => {
                            this.followedExecution = JSON.parse(event.data);
                        }
                    });
            },
            followLogs(executionId) {
                this.$store
                    .dispatch("execution/followLogs", {id: executionId})
                    .then(sse => {
                        this.logsSSE = sse;

                        this.logsSSE.onmessage = event => {
                            this.logsBuffer = this.logsBuffer.concat(JSON.parse(event.data));

                            clearTimeout(this.timeout);
                            this.timeout = setTimeout(() => {
                                this.timer = moment()
                                this.logs = this.logs.concat(this.logsBuffer);
                                this.logsBuffer = [];
                                this.scrollToBottomFailedTask();
                            }, 100);

                            // force at least 1 logs refresh / 500ms
                            if (moment().diff(this.timer, "seconds") > 0.5) {
                                clearTimeout(this.timeout);
                                this.timer = moment()
                                this.logs = this.logs.concat(this.logsBuffer);
                                this.logsBuffer = [];
                                this.scrollToBottomFailedTask();
                            }
                        }
                    })
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },
            expandAll() {
                if (!this.followedExecution) {
                    setTimeout(() => this.expandAll(), 50);
                    return;
                }

                this.shownAttemptsUid = this.currentTaskRuns.map(taskRun => this.attemptUid(
                    taskRun.id,
                    this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0
                ));
                this.shownAttemptsUid.forEach(attemptUid => this?.$refs?.[attemptUid]?.[0]?.scrollToBottom());

                this.expandSubflows();
            },
            expandSubflows() {
                if (this.currentTaskRuns.some(taskRun => this.isSubflow(taskRun))) {
                    const subflowLogsElements = this.$refs["subflows-logs"];
                    if (!subflowLogsElements || subflowLogsElements.length === 0) {
                        setTimeout(() => this.expandSubflows(), 50);
                    }

                    subflowLogsElements?.forEach(subflowLogs => subflowLogs.expandAll());
                }
            },
            collapseAll() {
                this.shownAttemptsUid = [];
            },
            attemptUid(taskRunId, attemptNumber) {
                return `${taskRunId}-${attemptNumber}`
            },
            scrollToBottomFailedTask() {
                if (this.autoExpandTaskrunStates.includes(this.followedExecution.state.current)) {
                    this.currentTaskRuns.forEach((taskRun) => {
                        if (taskRun.state.current === State.FAILED || taskRun.state.current === State.RUNNING) {
                            const attemptNumber = taskRun.attempts ? taskRun.attempts.length - 1 : (this.forcedAttemptNumber ?? 0)
                            if (this.shownAttemptsUid.includes(`${taskRun.id}-${attemptNumber}`)) {
                                this?.$refs?.[`${taskRun.id}-${attemptNumber}`]?.[0]?.scrollToBottom();
                            }
                        }
                    });
                }
            },
            uniqueTaskRunDisplayFilter(currentTaskRun) {
                return !(this.taskRunId && this.taskRunId !== currentTaskRun.id);
            },
            loadLogs(executionId) {
                if(!this.showLogs) {
                    return;
                }
                this.$store.dispatch("execution/loadLogs", {
                    executionId,
                    params: {
                        minLevel: this.level
                    },
                    store: false
                }).then(logs => {
                    this.logs = logs
                });
            },
            attempts(taskRun) {
                if (this.followedExecution.state.current === State.RUNNING || this.forcedAttemptNumber === undefined) {
                    return taskRun.attempts ?? [{state: taskRun.state}];
                }

                return taskRun.attempts ? [taskRun.attempts[this.forcedAttemptNumber]] : [];
            },
            toggleShowAttempt(attemptUid) {
                this.shownAttemptsUid = _xor(this.shownAttemptsUid, [attemptUid])
            },
            swapDisplayedAttempt(event) {
                const {taskRunId, attemptNumber: newDisplayedAttemptNumber} = event;
                this.shownAttemptsUid = this.shownAttemptsUid.map(attemptUid => attemptUid.startsWith(`${taskRunId}-`)
                    ? this.attemptUid(taskRunId, newDisplayedAttemptNumber)
                    : attemptUid
                );

                this.selectedAttemptNumberByTaskRunId[taskRunId] = newDisplayedAttemptNumber;
            },
            taskType(taskRun) {
                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if (task === undefined && parentTaskRunId) {
                    return this.taskType(this.taskRunById[parentTaskRunId])
                }
                return task ? task.type : undefined;
            }
        },
        beforeUnmount() {
            this.executionSSE?.close();
            this.logsSSE?.close();
        },
    };
</script>
<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .log-wrapper {
        &.even > div > .el-card {
            background: var(--bs-gray-100);

            html.dark & {
                background: var(--bs-gray-200);
            }

            .task-icon {
                border: none;
                color: $white;
            }
        }

        :deep(.vue-recycle-scroller__item-view + .vue-recycle-scroller__item-view) {
            border-top: 1px solid var(--bs-border-color);
        }

        :deep(.line) {
            padding-left: 0;
        }

        .attempt-wrapper {
            margin-bottom: var(--spacer);
            background-color: var(--bs-white);

            html.dark & {
                background-color: var(--bs-gray-100);
            }

            .attempt-wrapper & {
                border-radius: .25rem;
            }
        }

        .output {
            margin-right: 5px;
        }

        pre {
            border: 1px solid var(--light);
            background-color: var(--bs-gray-200);
            padding: 10px;
            margin-top: 5px;
            margin-bottom: 20px;
        }

        .log-lines {
            max-height: 50vh;
            transition: max-height 0.2s ease-out;
            margin-top: calc(var(--spacer) / 2);

            &::-webkit-scrollbar {
                width: 5px;
            }

            &::-webkit-scrollbar-track {
                background: var(--bs-gray-500);
            }

            &::-webkit-scrollbar-thumb {
                background: var(--bs-primary);
            }
        }
    }
</style>
