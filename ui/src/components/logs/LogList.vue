<template>
    <div v-if="followedExecution" class="log-wrapper">
        <div v-for="currentTaskRun in currentTaskRuns" :key="currentTaskRun.id">
            <template
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
            >
                <el-card class="attempt-wrapper">
                    <div class="attempt-header">
                        <div
                            class="task-icon"
                        >
                            <task-icon
                                :cls="taskIcon(currentTaskRun)"
                                v-if="taskIcon(currentTaskRun)"
                                only-icon
                            />
                        </div>
                        <div class="task-id flex-grow-1"
                             :id="`attempt-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${currentTaskRun.id}`"
                        >
                            <el-tooltip :persistent="false" transition="" :hide-after="0">
                                <template #content>
                                    {{ $t("from") }} :
                                    {{ $filters.date(selectedAttempt(currentTaskRun).state.startDate) }}
                                    <br>
                                    {{ $t("to") }} :
                                    {{ $filters.date(selectedAttempt(currentTaskRun).state.endDate) }}
                                    <br>
                                    <clock />
                                    <strong>{{ $t("duration") }}:</strong>
                                    {{ $filters.humanizeDuration(selectedAttempt(currentTaskRun).state.duration) }}
                                </template>
                                <span>
                                    <span class="me-1 fw-bold">{{ currentTaskRun.taskId }}</span>
                                    <small v-if="currentTaskRun.value">
                                        {{ currentTaskRun.value }}
                                    </small>
                                </span>
                            </el-tooltip>
                        </div>

                        <div class="task-status">
                            <status size="small" :status="selectedAttempt(currentTaskRun).state.current" />
                        </div>

                        <div class="task-duration">
                            <small class="me-1">
                                <duration :histories="selectedAttempt(currentTaskRun).state.histories" />
                            </small>
                        </div>

                        <el-select v-model="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                                   @change="swapDisplayedAttempt(currentTaskRun.id, $event)"
                                   :disabled="currentTaskRun.attempts?.length <= 1"
                        >
                            <el-option v-for="(_, index) in attempts(currentTaskRun)"
                                       :key="`attempt-${index}-${currentTaskRun.id}`"
                                       :value="index"
                                       :label="`${$t('attempt')} ${index + 1}`"
                            />
                        </el-select>

                        <el-button v-if="!taskRunId" class="border-0 expand-collapse" type="default" text
                                   @click="() => toggleShowAttempt(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                        >
                            <ChevronDown
                                v-if="!shownAttemptsUid.includes(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                            />
                            <ChevronUp v-else />
                        </el-button>

                        <el-dropdown trigger="click">
                            <el-button type="default" class="more-dropdown-button">
                                <DotsHorizontal title="" />
                            </el-button>
                            <template #dropdown>
                                <el-dropdown-menu>
                                    <sub-flow-link
                                        v-if="isSubflow(currentTaskRun)"
                                        component="el-dropdown-item"
                                        tab-execution="logs"
                                        :execution-id="currentTaskRun.outputs.executionId"
                                    />

                                    <metrics :task-run="currentTaskRun" :execution="followedExecution" />

                                    <outputs
                                        :outputs="currentTaskRun.outputs"
                                        :execution="followedExecution"
                                    />

                                    <restart
                                        component="el-dropdown-item"
                                        :key="`restart-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                                        is-replay
                                        tooltip-position="left"
                                        :execution="followedExecution"
                                        :task-run="currentTaskRun"
                                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                                        @follow="forwardEvent('follow', $event)"
                                    />

                                    <change-status
                                        component="el-dropdown-item"
                                        :key="`change-status-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                                        :execution="followedExecution"
                                        :task-run="currentTaskRun"
                                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                                        @follow="forwardEvent('follow', $event)"
                                    />
                                    <task-edit
                                        :read-only="true"
                                        component="el-dropdown-item"
                                        :task-id="currentTaskRun.taskId"
                                        :section="SECTIONS.TASKS"
                                        :flow-id="followedExecution.flowId"
                                        :namespace="followedExecution.namespace"
                                        :revision="followedExecution.flowRevision"
                                        :flow-source="flow?.source"
                                    />
                                    <el-dropdown-item
                                        :icon="Download"
                                        @click="downloadContent(currentTaskRun.id)"
                                    >
                                        {{ $t("download") }}
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
                    </div>
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun.id)"
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
                                    v-if="filter === '' || item.message.toLowerCase().includes(filter)"
                                />
                                <log-list v-if="!taskRunId && isSubflow(currentTaskRun) && currentTaskRun.outputs?.executionId"
                                          ref="subflows-logs"
                                          :level="level"
                                          :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                                          :filter="filter"
                                          :allow-auto-expand-subflows="false"
                                          :target-execution-id="currentTaskRun.outputs.executionId"
                                          :class="$el.classList.contains('even') ? '' : 'even'"
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
    import Restart from "../executions/Restart.vue";
    import ChangeStatus from "../executions/ChangeStatus.vue";
    import Metrics from "../executions/Metrics.vue";
    import Outputs from "../executions/Outputs.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import State from "../../utils/state";
    import Status from "../Status.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import TaskEdit from "../flows/TaskEdit.vue";
    import Duration from "../layout/Duration.vue";
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import _xor from "lodash/xor";
    import _groupBy from "lodash/groupBy";
    import FlowUtils from "../../utils/flowUtils.js";
    import moment from "moment";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {logDisplayTypes, SECTIONS} from "../../utils/constants";
    import Download from "vue-material-design-icons/Download.vue";
    import DotsHorizontal from "vue-material-design-icons/DotsHorizontal.vue";
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";

    export default {
        name: 'LogList',
        components: {
            LogLine,
            Restart,
            ChangeStatus,
            Clock,
            Metrics,
            Outputs,
            ChevronDown,
            ChevronUp,
            Status,
            SubFlowLink,
            TaskEdit,
            Duration,
            TaskIcon,
            DynamicScroller,
            DynamicScrollerItem,
            DotsHorizontal
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
                            "flow/loadFlowNoCommit",
                            {
                                namespace: newExecution.namespace,
                                id: newExecution.flowId,
                                revision: newExecution.flowRevision
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
            SECTIONS() {
                return SECTIONS
            },
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
                    .filter(logLine => this.filter === "" || logLine.message.toLowerCase().includes(this.filter) || this.isSubflow(this.taskRunById[logLine.taskRunId]))
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
                if(this.autoExpandTaskrunStates.length === 0) {
                    return;
                }

                if(this.followedExecution === undefined) {
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
            shouldDisplayLogs(taskRunId) {
                return this.shownAttemptsUid.includes(this.attemptUid(taskRunId, this.selectedAttemptNumberByTaskRunId[taskRunId])) &&
                    this.logsWithIndexByAttemptUid[this.attemptUid(taskRunId, this.selectedAttemptNumberByTaskRunId[taskRunId])]
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
            swapDisplayedAttempt(taskRunId, newDisplayedAttemptUid) {
                this.shownAttemptsUid = this.shownAttemptsUid.map(attemptUid => attemptUid.startsWith(`${taskRunId}-`)
                    ? this.attemptUid(taskRunId, newDisplayedAttemptUid)
                    : attemptUid
                );
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },
            selectedAttempt(taskRun) {
                return this.attempts(taskRun)[this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0];
            },
            expandAll() {
                if(!this.followedExecution) {
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
                if(this.currentTaskRuns.some(taskRun => this.isSubflow(taskRun))){
                    const subflowLogsElements = this.$refs["subflows-logs"];
                    if(!subflowLogsElements || subflowLogsElements.length === 0) {
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
            downloadContent(currentTaskRunId) {
                const params = this.params
                this.$store.dispatch("execution/downloadLogs", {
                    executionId: this.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId}
                }).then((response) => {
                    const url = window.URL.createObjectURL(new Blob([response]));
                    const link = document.createElement("a");
                    link.href = url;
                    link.setAttribute("download", this.downloadName(currentTaskRunId));
                    document.body.appendChild(link);
                    link.click();
                });
            },
            downloadName(currentTaskRunId) {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.followedExecution.id}-${currentTaskRunId}.log`
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            uniqueTaskRunDisplayFilter(currentTaskRun) {
                return !(this.taskRunId && this.taskRunId !== currentTaskRun.id);
            },
            taskIcon(taskRun) {
                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if(task === undefined && parentTaskRunId) {
                    return this.taskIcon(this.taskRunById[parentTaskRunId])
                }
                return task ? task.type : undefined;
            },
            loadLogs(executionId) {
                this.$store.dispatch("execution/loadLogsNoCommit", {
                    executionId, params: {
                        minLevel: this.level
                    }
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

        .attempt-header {
            display: flex;
            gap: calc(var(--spacer) / 2);

            > * {
                display: flex;
                align-items: center;
            }

            .el-select {
                width: 8rem;
            }

            .attempt-number {
                background: var(--bs-gray-400);
                padding: .375rem .75rem;
                white-space: nowrap;

                html.dark & {
                    color: var(--bs-gray-600);
                }
            }

            .task-id, .task-duration {
                padding: .375rem 0;
            }

            .task-id {
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;

                span span {
                    color: var(--bs-tertiary-color);

                    html:not(.dark) & {
                        color: $black;
                    }
                }
            }

            .task-icon {
                width: 36px;
                padding: 6px;
                border-radius: $border-radius-lg;
            }

            small {
                color: var(--bs-gray-500);
            }

            .task-duration small {
                white-space: nowrap;

                color: var(--bs-gray-800);
            }

            .more-dropdown-button {
                padding: .5rem;
                border: 1px solid rgba($white, .05);

                &:not(:hover) {
                    background: rgba($white, .10);
                }
            }

            .expand-collapse {
                background-color: transparent !important;
            }
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
