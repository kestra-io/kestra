<template>
    <DynamicScroller
        v-if="followedExecution"
        ref="taskRunScroller"
        :items="currentTaskRuns"
        :min-item-size="50"
        key-field="id"
        class="log-wrapper"
    >
        <template #default="{item: currentTaskRun, index: currentTaskRunIndex, active: isTaskRunActive}">
            <DynamicScrollerItem
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
                :item="currentTaskRun"
                :active="isTaskRunActive"
                :data-index="currentTaskRunIndex"
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
                        :logs="filteredLogs"
                        @update-logs="loadLogs"
                    >
                        <template #buttons>
                            <div id="buttons" />
                        </template>
                    </task-run-line>
                    <for-each-status
                        v-if="shouldDisplayProgressBar(currentTaskRun)"
                        :execution-id="currentTaskRun.executionId"
                        :subflows-status="forEachItemExecutableByRootTaskId[currentTaskRun.taskId].outputs.iterations"
                        :max="forEachItemExecutableByRootTaskId[currentTaskRun.taskId].outputs.numberOfBatches"
                    />
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun)"
                        :items="logsWithIndexByAttemptUid[attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])] ?? []"
                        :min-item-size="0.1"
                        key-field="index"
                        class="log-lines"
                        :ref="el => logsScrollerRef(el, currentTaskRunIndex, attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, index, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :size-dependencies="[item.message, item.image]"
                                :data-index="index"
                            >
                                <Teleport v-if="item.logFile" to="#buttons">
                                    <el-button-group class="line">
                                        <a class="el-button el-button--small el-button--primary" :href="fileUrl(item.logFile)" target="_blank">
                                            <Download />
                                            {{ $t('download') }}
                                        </a>
                                        <FilePreview :value="item.logFile" :execution-id="followedExecution.id" />
                                        <el-button disabled size="small" type="primary" v-if="logFileSizeByPath[item.logFile]">
                                            ({{ logFileSizeByPath[item.logFile] }})
                                        </el-button>
                                    </el-button-group>
                                </Teleport>
                                <log-line
                                    @click="emitLogCursor(`${currentTaskRunIndex}/${index}`)"
                                    class="line"
                                    :cursor="logCursor === `${currentTaskRunIndex}/${index}`"
                                    :class="{['log-bg-' + levelToHighlight?.toLowerCase()]: levelToHighlight === item.level, 'opacity-40': levelToHighlight && levelToHighlight !== item.level}"
                                    :key="index"
                                    :level="level"
                                    :log="item"
                                    :exclude-metas="excludeMetas"
                                    v-else-if="filter === '' || item.message?.toLowerCase().includes(filter)"
                                />
                                <TaskRunDetails
                                    v-if="!taskRunId && isSubflow(currentTaskRun) && shouldDisplaySubflow(index, currentTaskRun) && currentTaskRun.outputs?.executionId"
                                    :ref="el => subflowTaskRunDetailsRef(el, currentTaskRunIndex + '/' + index)"
                                    :log-cursor="logCursor?.split('/')?.slice(2).join('/')"
                                    @log-cursor="emitLogCursor(currentTaskRunIndex + '/' + index + '/' + $event)"
                                    @log-indices-by-level="childLogIndicesByLevel(currentTaskRunIndex, index, $event)"
                                    :level-to-highlight="levelToHighlight"
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
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script>
    import LogLine from "./LogLine.vue";
    import {State} from "@kestra-io/ui-libs"
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
    import throttle from "lodash/throttle";
    import FilePreview from "../executions/FilePreview.vue";
    import {apiUrl} from "override/utils/route.js";
    import Utils from "../../utils/utils.js";
    import LogUtils from "../../utils/logs.js";

    export default {
        name: "TaskRunDetails",
        components: {
            FilePreview,
            TaskRunLine,
            ForEachStatus,
            LogLine,
            DynamicScroller,
            DynamicScrollerItem,
            Download
        },
        emits: ["opened-taskruns-count", "follow", "reset-expand-collapse-all-switch", "log-cursor", "log-indices-by-level"],
        props: {
            logCursor: {
                type: String,
                default: undefined,
            },
            levelToHighlight: {
                type: String,
                default: undefined
            },
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
                required: false,
                default: undefined
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
                rawLogs: [],
                timer: undefined,
                timeout: undefined,
                selectedAttemptNumberByTaskRunId: {},
                followedExecution: undefined,
                executionSSE: undefined,
                logsSSE: undefined,
                flow: undefined,
                logsBuffer: [],
                shownSubflowsIds: [],
                logFileSizeByPath: {},
                throttledExecutionUpdate: throttle(function (event) {
                    this.followedExecution = JSON.parse(event.data)
                }, 500),
                selectedLogLevel: undefined,
                childrenLogIndicesByLevelByChildUid: {},
                logsScrollerRefs: {},
                subflowTaskRunDetailsRefs: {}
            };
        },
        watch: {
            "shownAttemptsUid.length": function (openedTaskrunsCount) {
                this.$emit("opened-taskruns-count", openedTaskrunsCount);
            },
            level: function () {
                this.rawLogs = [];
                this.loadLogs(this.followedExecution.id);
            },
            execution: function () {
                if (this.execution && this.execution.state.current !== State.RUNNING && this.execution.state.current !== State.PAUSED) {
                    this.closeExecutionSSE();
                }
            },
            currentTaskRuns: {
                handler(taskRuns) {
                    // by default we preselect the last attempt for each task run
                    this.selectedAttemptNumberByTaskRunId = Object.fromEntries(taskRuns.map(taskRun => [taskRun.id, this.forcedAttemptNumber ?? this.attempts(taskRun).length - 1]));
                    this.autoExpandBasedOnSettings();
                },
                immediate: true,
                deep: true
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
                handler: async function (newExecution, oldExecution) {
                    if (!newExecution) {
                        return;
                    }

                    if (!oldExecution) {
                        this.$nextTick(() => {
                            const parentScroller = this.$refs.taskRunScroller?.$el?.parentNode?.closest(".vue-recycle-scroller");
                            if (parentScroller) {
                                const scrollerStyles = window.getComputedStyle(parentScroller);
                                this.$refs.taskRunScroller.$el.style.maxHeight = `${scrollerStyles.getPropertyValue("max-height") - parentScroller.clientHeight}px`;
                            }
                        })
                    }

                    if (!this.targetFlow) {
                        this.flow = await this.$store.dispatch(
                            "execution/loadFlowForExecution",
                            {
                                namespace: newExecution.namespace,
                                flowId: newExecution.flowId,
                                revision: newExecution.flowRevision
                            }
                        );
                    }

                    if (![State.RUNNING, State.PAUSED].includes(this.followedExecution.state.current)) {
                        this.closeExecutionSSE()
                        // wait a bit to make sure we don't miss logs as log indexer is asynchronous
                        setTimeout(() => {
                            this.closeLogsSSE()
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
            },
            allLogIndicesByLevel() {
                this.$emit("log-indices-by-level", this.allLogIndicesByLevel);
            },
            logCursor(newValue) {
                if (newValue !== undefined) {
                    this.scrollToLog(newValue);
                }
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
            ...mapState("auth", ["user"]),
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
                const logFilesWrappers = this.currentTaskRuns.flatMap(taskRun =>
                    this.attempts(taskRun)
                        .filter(attempt => attempt.logFile !== undefined)
                        .map((attempt, attemptNumber) => ({logFile: attempt.logFile, taskRunId: taskRun.id, attemptNumber}))
                );

                logFilesWrappers.forEach(logFileWrapper => this.fetchAndStoreLogFileSize(logFileWrapper.logFile))

                const indexedLogs = [...this.filteredLogs, ...logFilesWrappers]
                    .filter(logLine => logLine.logFile !== undefined || (this.filter === "" || logLine?.message.toLowerCase().includes(this.filter) || this.isSubflow(this.taskRunById[logLine.taskRunId])))
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
            },
            taskTypeAndTaskRunByTaskId() {
                return Object.fromEntries(this.followedExecution?.taskRunList?.map(taskRun => [taskRun.taskId, [this.taskType(taskRun), taskRun]]));
            },
            forEachItemExecutableByRootTaskId() {
                return Object.fromEntries(
                    Object.entries(this.taskTypeAndTaskRunByTaskId)
                        .filter(([, taskTypeAndTaskRun]) => taskTypeAndTaskRun[0] === "io.kestra.plugin.core.flow.ForEachItem" || taskTypeAndTaskRun[0] === "io.kestra.core.tasks.flows.ForEachItem")
                        .map(([taskId]) => [taskId, this.taskTypeAndTaskRunByTaskId?.[taskId + "_items"]?.[1]])
                );
            },
            currentTaskRunsLogIndicesByLevel() {
                return this.currentTaskRuns.reduce((currentTaskRunsLogIndicesByLevel, taskRun, taskRunIndex) => {
                    if (this.shouldDisplayLogs(taskRun)) {
                        const currentTaskRunLogs = this.logsWithIndexByAttemptUid[this.attemptUid(taskRun.id, this.selectedAttemptNumberByTaskRunId[taskRun.id])];
                        currentTaskRunLogs?.forEach((log, logIndex) => {
                            currentTaskRunsLogIndicesByLevel[log.level] = [...(currentTaskRunsLogIndicesByLevel?.[log.level] ?? []), taskRunIndex + "/" + logIndex];
                        });
                    }

                    return currentTaskRunsLogIndicesByLevel
                }, {});
            },
            allLogIndicesByLevel() {
                const currentTaskRunsLogIndicesByLevel = {...this.currentTaskRunsLogIndicesByLevel};
                return Object.entries(this.childrenLogIndicesByLevelByChildUid).reduce((allLogIndicesByLevel, [logUid, childrenLogIndicesByLevel]) => {
                    Object.entries(childrenLogIndicesByLevel).forEach(([level, logIndices]) => {
                        allLogIndicesByLevel[level] = [...(allLogIndicesByLevel?.[level] ?? []), ...logIndices.map(logIndex => logUid + "/" + logIndex)];
                    });

                    return allLogIndicesByLevel;
                }, currentTaskRunsLogIndicesByLevel);
            },
            levelOrLower() {
                return LogUtils.levelOrLower(this.level);
            },
            filteredLogs() {
                return this.rawLogs.filter(log => this.levelOrLower.includes(log.level));
            }
        },
        methods: {
            fileUrl(path) {
                return `${apiUrl(this.$store)}/executions/${this.followedExecution.id}/file?path=${path}`;
            },
            async fetchAndStoreLogFileSize(path){
                if (this.logFileSizeByPath[path] !== undefined) {
                    return;
                }

                const axiosResponse = await this.$http(`${apiUrl(this.$store)}/executions/${this.followedExecution.id}/file/metas?path=${path}`, {
                    validateStatus: (status) => status === 200 || status === 404 || status === 422
                });
                this.logFileSizeByPath[path] = Utils.humanFileSize(axiosResponse.data.size);
            },
            closeExecutionSSE() {
                if (this.executionSSE) {
                    this.executionSSE.close();
                    this.executionSSE = undefined;
                }
            },
            closeLogsSSE() {
                if (this.logsSSE) {
                    this.logsSSE.close();
                    this.logsSSE = undefined;
                }
            },
            toggleExpandCollapseAll() {
                if(this.shownAttemptsUid.length === 0){
                    this.expandAll()
                } else {
                    this.collapseAll()
                }
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
                        this.showAttempt(this.attemptUid(taskRun.id, this.selectedAttemptNumberByTaskRunId[taskRun.id]));
                    }
                });
            },
            shouldDisplayProgressBar(taskRun) {
                return this.showProgressBar &&
                    (this.taskType(taskRun) === "io.kestra.plugin.core.flow.ForEachItem" || this.taskType(taskRun) === "io.kestra.core.tasks.flows.ForEachItem") &&
                    this.forEachItemExecutableByRootTaskId[taskRun.taskId]?.outputs?.iterations !== undefined &&
                    this.forEachItemExecutableByRootTaskId[taskRun.taskId]?.outputs?.numberOfBatches !== undefined;
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
                        this.executionSSE.onmessage = executionEvent => {
                            const isEnd = executionEvent && executionEvent.lastEventId === "end";
                            if (isEnd) {
                                this.closeExecutionSSE();
                            }
                            // we are receiving a first "fake" event to force initializing the connection: ignoring it
                            if (executionEvent.lastEventId !== "start") {
                                this.throttledExecutionUpdate(executionEvent);
                            }
                            if (isEnd) {
                                this.throttledExecutionUpdate.flush();
                            }
                        }
                    });
            },
            followLogs(executionId) {
                this.$store
                    .dispatch("execution/followLogs", {id: executionId})
                    .then(sse => {
                        this.logsSSE = sse;

                        this.logsSSE.onmessage = event => {
                            // we are receiving a first "fake" event to force initializing the connection: ignoring it
                            if (event.lastEventId !== "start") {
                                this.logsBuffer = this.logsBuffer.concat(JSON.parse(event.data));
                            }

                            clearTimeout(this.timeout);
                            this.timeout = setTimeout(() => {
                                this.timer = moment()
                                this.rawLogs = this.rawLogs.concat(this.logsBuffer);
                                this.logsBuffer = [];
                                this.scrollToBottomFailedTask();
                            }, 100);

                            // force at least 1 logs refresh / 500ms
                            if (moment().diff(this.timer, "seconds") > 0.5) {
                                clearTimeout(this.timeout);
                                this.timer = moment()
                                this.rawLogs = this.rawLogs.concat(this.logsBuffer);
                                this.logsBuffer = [];
                                this.scrollToBottomFailedTask();
                            }
                        }
                    })
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },

            shouldDisplaySubflow(taskRunIndex, taskRun) {
                const subflowExecutionId = taskRun.outputs.executionId;
                const index = this.shownSubflowsIds.findIndex(item => item.subflowExecutionId === subflowExecutionId)
                if (index === -1) {
                    this.shownSubflowsIds.push({subflowExecutionId: subflowExecutionId, taskRunIndex: taskRunIndex});
                    return true;
                } else {
                    return this.shownSubflowsIds[index].taskRunIndex === taskRunIndex;
                }
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
                this.shownAttemptsUid.forEach(attemptUid => this.logsScrollerRefs?.[attemptUid]?.[0]?.scrollToBottom());

                this.expandSubflows();
            },
            expandSubflows() {
                if (this.currentTaskRuns.some(taskRun => this.isSubflow(taskRun))) {
                    const subflowLogsElements = Object.values(this.subflowTaskRunDetailsRefs);
                    if (subflowLogsElements.length === 0) {
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
                                this.logsScrollerRefs?.[`${taskRun.id}-${attemptNumber}`]?.[0]?.scrollToBottom();
                            }
                        }
                    });
                }
            },
            uniqueTaskRunDisplayFilter(currentTaskRun) {
                return !(this.taskRunId && this.taskRunId !== currentTaskRun.id);
            },
            loadLogs(executionId) {
                if (!this.showLogs) {
                    return;
                }

                this.$store.dispatch("execution/loadLogs", {
                    executionId,
                    params: {
                        minLevel: this.level
                    }
                }).then(logs => {
                    this.rawLogs = logs
                });
            },
            attempts(taskRun) {
                if (this.followedExecution.state.current === State.RUNNING || this.forcedAttemptNumber === undefined) {
                    return taskRun.attempts ?? [{state: taskRun.state}];
                }

                return taskRun.attempts ? [taskRun.attempts[this.forcedAttemptNumber]] : [];
            },
            showAttempt(attemptUid) {
                if (!this.shownAttemptsUid.includes(attemptUid)) {
                    this.shownAttemptsUid.push(attemptUid);
                }
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
                if (!taskRun) return undefined;

                const task = FlowUtils.findTaskById(this.flow, taskRun?.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if (task === undefined && parentTaskRunId) {
                    return this.taskType(this.taskRunById[parentTaskRunId])
                }
                return task ? task.type : undefined;
            },
            emitLogCursor(logCursor) {
                this.$emit("log-cursor", logCursor);
            },
            childLogIndicesByLevel(taskRunIndex, logIndex, logIndicesByLevel) {
                this.childrenLogIndicesByLevelByChildUid[`${taskRunIndex}/${logIndex}`] = logIndicesByLevel;
            },
            logsScrollerRef(el, ...ids) {
                ids.forEach(id => this.logsScrollerRefs[id] = el);
            },
            subflowTaskRunDetailsRef(el, id) {
                this.subflowTaskRunDetailsRefs[id] = el;
            },
            scrollToLog(logId) {
                const split = logId.split("/");
                this.$refs.taskRunScroller.scrollToItem(split[0]);
                this.logsScrollerRefs?.[split[0]]?.scrollToItem(split[1]);
                if (split.length > 2) {
                    this.subflowTaskRunDetailsRefs?.[split[0] + "/" + split[1]]?.scrollToLog(split.slice(2).join("/"));
                }
            }
        },
        beforeUnmount() {
            this.closeExecutionSSE();
            this.closeLogsSSE()
        },
    };
</script>
<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .log-wrapper {
        max-height: calc(100vh - 233px);

        &::-webkit-scrollbar {
            width: 2px;
            height: 2px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 0px;
        }

        :deep(> .vue-recycle-scroller__item-wrapper > .vue-recycle-scroller__item-view > div) {
            padding-bottom: 1rem;
        }

        :deep(.line) {
            padding-left: 0;
        }

        .attempt-wrapper {
            background-color: var(--ks-background-input);
            margin-bottom: 0;
            border: 1px solid var(--ks-border-primary);

            :deep(.el-card__body) {
                padding: 0;
            }

            .attempt-wrapper & {
                border-radius: .25rem;
            }

            tbody:last-child & {
                border-bottom: 1px solid var(--ks-border-primary);
            }

            .attempt-header {
                padding: 0 .5rem .5rem;
                border-bottom: 1px solid var(--ks-border-primary);
            }

            .line {
                padding: .5rem;
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

            .line {
                padding: 1rem;

                &.cursor {
                    background-color: var(--bs-gray-300)
                }
            }

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
    }
</style>