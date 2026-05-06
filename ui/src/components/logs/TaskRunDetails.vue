<template>
    <DynamicScroller
        v-if="followedExecution && currentTaskRuns.length > 0"
        ref="taskRunScroller"
        :items="currentTaskRuns"
        :minItemSize="50"
        keyField="id"
        class="log-wrapper"
    >
        <template
            #default="{
                item: currentTaskRun,
                index: currentTaskRunIndex,
                active: isTaskRunActive,
            }"
        >
            <DynamicScrollerItem
                v-if="uniqueTaskRunDisplayFilter(currentTaskRun)"
                :item="currentTaskRun"
                :active="isTaskRunActive"
                :data-index="currentTaskRunIndex"
            >
                <KsCard class="attempt-wrapper">
                    <TaskRunLine
                        :currentTaskRun="currentTaskRun"
                        :followedExecution="followedExecution"
                        :flow="flow"
                        :forcedAttemptNumber="forcedAttemptNumber"
                        :taskRunId="taskRunId"
                        :selectedAttemptNumberByTaskRunId="
                            selectedAttemptNumberByTaskRunId
                        "
                        :shownAttemptsUid="shownAttemptsUid"
                        :logs="filteredLogs"
                        @toggle-show-attempt="toggleShowAttempt"
                        @swap-displayed-attempt="swapDisplayedAttempt"
                        @update-logs="loadLogs"
                    >
                        <template #buttons>
                            <div id="buttons" />
                        </template>
                    </TaskRunLine>
                    <DynamicScroller
                        v-if="shouldDisplayLogs(currentTaskRun)"
                        :items="
                            logsWithIndexByAttemptUid[
                                attemptUid(
                                    currentTaskRun.id,
                                    selectedAttemptNumberByTaskRunId[
                                        currentTaskRun.id
                                    ],
                                )
                            ] ?? []
                        "
                        :minItemSize="1"
                        keyField="index"
                        class="log-lines"
                        :class="{'single-line': currentTaskRuns.length === 1}"
                        :ref="
                            (el) =>
                                logsScrollerRef(
                                    el,
                                    currentTaskRunIndex,
                                    attemptUid(
                                        currentTaskRun.id,
                                        selectedAttemptNumberByTaskRunId[
                                            currentTaskRun.id
                                        ],
                                    ),
                                )
                        "
                        @resize="scrollToBottomFailedTask"
                    >
                        <template #default="{item, index, active}">
                            <DynamicScrollerItem
                                :item="item"
                                :active="active"
                                :sizeDependencies="[item.message, item.image]"
                                :data-index="index"
                            >
                                <Teleport v-if="item.logFile" to="#buttons">
                                    <KsButtonGroup class="line">
                                        <KsButton
                                            type="primary"
                                            tag="a"
                                            :href="fileUrl(item.logFile)"
                                            target="_blank"
                                            size="small"
                                            :icon="Download"
                                            rel="noopener noreferrer"
                                        >
                                            {{ $t("download") }}
                                        </KsButton>
                                        <FilePreview
                                            :value="item.logFile"
                                            :executionId="followedExecution.id"
                                        />
                                        <KsButton
                                            disabled
                                            size="small"
                                            type="primary"
                                            v-if="
                                                logFileSizeByPath[item.logFile]
                                            "
                                        >
                                            ({{
                                                logFileSizeByPath[item.logFile]
                                            }})
                                        </KsButton>
                                    </KsButtonGroup>
                                </Teleport>
                                <LogLine
                                    class="line"
                                    :cursor="
                                        logCursor ===
                                            `${currentTaskRunIndex}/${index}`
                                    "
                                    :class="{
                                        ['log-bg-' +
                                            levelToHighlight?.toLowerCase()]:
                                                levelToHighlight === item.level,
                                        'opacity-40':
                                            levelToHighlight &&
                                            levelToHighlight !== item.level,
                                    }"
                                    :key="index"
                                    :level="level"
                                    :log="item"
                                    :excludeMetas="excludeMetas"
                                    v-else-if="
                                        filter === '' ||
                                            item.message
                                                ?.toLowerCase()
                                                .includes(filter.toLowerCase())
                                    "
                                />
                                <TaskRunDetails
                                    v-if="
                                        !taskRunId &&
                                            isSubflow(currentTaskRun) &&
                                            shouldDisplaySubflow(
                                                index,
                                                currentTaskRun,
                                            ) &&
                                            currentTaskRun.outputs?.executionId
                                    "
                                    :ref="
                                        (el) =>
                                            subflowTaskRunDetailsRef(
                                                el,
                                                currentTaskRunIndex +
                                                    '/' +
                                                    index,
                                            )
                                    "
                                    :logCursor="
                                        logCursor
                                            ?.split('/')
                                            ?.slice(2)
                                            .join('/')
                                    "
                                    @log-cursor="
                                        emitLogCursor(
                                            currentTaskRunIndex +
                                                '/' +
                                                index +
                                                '/' +
                                                $event,
                                        )
                                    "
                                    @log-indices-by-level="
                                        childLogIndicesByLevel(
                                            currentTaskRunIndex,
                                            index,
                                            $event,
                                        )
                                    "
                                    :levelToHighlight="levelToHighlight"
                                    :level="level"
                                    :excludeMetas="[
                                        'namespace',
                                        'flowId',
                                        'taskId',
                                        'executionId',
                                    ]"
                                    :filter="filter"
                                    :allowAutoExpandSubflows="false"
                                    :targetExecutionId="
                                        currentTaskRun.outputs.executionId
                                    "
                                    :class="
                                        $el.classList.contains('even')
                                            ? ''
                                            : 'even'
                                    "
                                    :showProgressBar="showProgressBar"
                                    :showLogs="showLogs"
                                />
                            </DynamicScrollerItem>
                        </template>
                    </DynamicScroller>
                </KsCard>
            </DynamicScrollerItem>
        </template>
    </DynamicScroller>
</template>

<script setup>
    import Download from "vue-material-design-icons/Download.vue";
</script>

<script>
    import LogLine from "./LogLine.vue";
    import {State} from "@kestra-io/design-system";
    import _xor from "lodash/xor";
    import _groupBy from "lodash/groupBy";
    import moment from "moment";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css";
    import {logDisplayTypes} from "../../utils/constants";
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import {mapStores} from "pinia";
    import {useCoreStore} from "../../stores/core";
    import {useExecutionsStore} from "../../stores/executions";
    import TaskRunLine from "../executions/TaskRunLine.vue";
    import FlowUtils from "../../utils/flowUtils";
    import FilePreview from "../executions/FilePreview.vue";
    import {apiUrl} from "override/utils/route";
    import Utils from "../../utils/utils";
    import * as LogUtils from "../../utils/logs";
    import throttle from "lodash/throttle";
    import {useAxios} from "../../utils/axios";

    export default {
        name: "TaskRunDetails",
        components: {
            FilePreview,
            TaskRunLine,
            LogLine,
            DynamicScroller,
            DynamicScrollerItem,
        },
        emits: [
            "opened-taskruns-count",
            "follow",
            "reset-expand-collapse-all-switch",
            "log-cursor",
            "log-indices-by-level",
        ],
        props: {
            logCursor: {
                type: String,
                default: undefined,
            },
            levelToHighlight: {
                type: String,
                default: undefined,
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
                default: undefined,
            },
            // allows to fetch the execution at startup
            targetExecutionId: {
                type: String,
                default: undefined,
            },
            // allows to pass directly a flow source (since it is already fetched by parent component)
            targetFlow: {
                type: Object,
                default: undefined,
            },
            allowAutoExpandSubflows: {
                type: Boolean,
                default: true,
            },
            showProgressBar: {
                type: Boolean,
                default: true,
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
                executionSSE: undefined,
                logsSSE: undefined,
                flow: undefined,
                logsBuffer: [],
                shownSubflowsIds: [],
                logFileSizeByPath: {},
                selectedLogLevel: undefined,
                childrenLogIndicesByLevelByChildUid: {},
                logsScrollerRefs: {},
                subflowTaskRunDetailsRefs: {},
                throttledExecutionUpdate: undefined,
                targetExecution: undefined,
            };
        },
        watch: {
            "shownAttemptsUid.length": function (openedTaskrunsCount) {
                this.$emit("opened-taskruns-count", openedTaskrunsCount);
            },
            level: function () {
                this.rawLogs = [];
                if(this.followedExecution)
                    this.loadLogs(this.followedExecution.id);
            },
            currentTaskRuns: {
                handler(taskRuns) {
                    // by default we preselect the last attempt for each task run
                    this.selectedAttemptNumberByTaskRunId = Object.fromEntries(
                        taskRuns.map((taskRun) => [
                            taskRun.id,
                            this.forcedAttemptNumber ??
                                this.attempts(taskRun).length - 1,
                        ]),
                    );
                    this.autoExpandBasedOnSettings();
                },
                immediate: true,
                deep: true,
            },
            targetFlow: {
                handler: function (flowSource) {
                    if (flowSource) {
                        this.flow = flowSource;
                    }
                },
                immediate: true,
            },
            followedExecution: {
                handler: async function (newExecution, oldExecution) {
                    if (!newExecution) {
                        return;
                    }

                    if (!oldExecution) {
                        this.$nextTick(() => {
                            const parentScroller =
                                this.$refs.taskRunScroller?.$el?.parentNode?.closest(
                                    ".vue-recycle-scroller",
                                );
                            if (parentScroller) {
                                const scrollerStyles =
                                    window.getComputedStyle(parentScroller);
                                this.$refs.taskRunScroller.$el.style.maxHeight = `${scrollerStyles.getPropertyValue("max-height") - parentScroller.clientHeight}px`;
                            }
                        });
                    }

                    if (!this.targetFlow) {
                        this.flow = await this.executionsStore.loadFlowForExecution(
                            {
                                namespace: newExecution.namespace,
                                flowId: newExecution.flowId,
                                revision: newExecution.flowRevision,
                                store: false,
                            },
                        );
                    }

                    if (!State.isRunning(this.followedExecution.state.current)) {
                        // wait a bit to make sure we don't miss logs as log indexer is asynchronous
                        setTimeout(() => {
                            this.closeLogsSSE();
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
                immediate: true,
            },
            allLogIndicesByLevel() {
                this.$emit("log-indices-by-level", this.allLogIndicesByLevel);
            },
            logCursor(newValue) {
                if (newValue !== undefined) {
                    this.scrollToLog(newValue);
                }
            },
        },
        mounted() {
            this.throttledExecutionUpdate = throttle((executionEvent) => {
                this.targetExecution = JSON.parse(executionEvent.data);
            }, 500);

            if (this.targetExecutionId) {
                this.followExecution(this.targetExecutionId);
            }

            this.autoExpandBasedOnSettings();
        },
        setup(){
            const $http = useAxios();
            return {
                $http
            }
        },
        computed: {
            ...mapStores(useCoreStore, useExecutionsStore),
            followedExecution() {
                return this.targetExecutionId === undefined
                    ? this.executionsStore.execution
                    : this.targetExecution;
            },
            Download() {
                return Download;
            },
            currentTaskRuns() {
                return (
                    this.followedExecution?.taskRunList?.filter((tr) =>
                        this.taskRunId ? tr.id === this.taskRunId : true,
                    ) ?? []
                );
            },
            params() {
                let params = {minLevel: this.level};

                if (this.taskRunId) {
                    params.taskId = this.taskRunById[this.taskRunId]?.taskId;

                    if (this.forcedAttemptNumber) {
                        params.attempt = this.forcedAttemptNumber;
                    }
                }

                return params;
            },
            taskRunById() {
                return Object.fromEntries(
                    this.currentTaskRuns.map((taskRun) => [taskRun.id, taskRun]),
                );
            },
            logsWithIndexByAttemptUid() {
                const logFilesWrappers = this.currentTaskRuns.flatMap((taskRun) =>
                    this.attempts(taskRun)
                        .filter((attempt) => attempt.logFile !== undefined)
                        .map((attempt, attemptNumber) => ({
                            logFile: attempt.logFile,
                            taskRunId: taskRun.id,
                            attemptNumber,
                        })),
                );

                logFilesWrappers.forEach((logFileWrapper) =>
                    this.fetchAndStoreLogFileSize(logFileWrapper.logFile),
                );

                const indexedLogs = [...this.filteredLogs, ...logFilesWrappers]
                    .filter(
                        (logLine) =>
                            logLine.logFile !== undefined ||
                            this.filter === "" ||
                            logLine?.message
                                .toLowerCase()
                                .includes(this.filter.toLowerCase()) ||
                            this.isSubflow(this.taskRunById[logLine.taskRunId]),
                    )
                    .map((logLine, index) => ({...logLine, index}));

                return _groupBy(indexedLogs, (indexedLog) =>
                    this.attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber),
                );
            },
            autoExpandTaskRunStates() {
                switch (
                    localStorage.getItem("logDisplay") ||
                    logDisplayTypes.DEFAULT
                ) {
                case logDisplayTypes.ERROR:
                    return [State.FAILED, State.RUNNING, State.PAUSED];
                case logDisplayTypes.ALL:
                    return State.arrayAllStates().map((s) => s.name);
                case logDisplayTypes.HIDDEN:
                    return [];
                default:
                    return State.arrayAllStates().map((s) => s.name);
                }
            },
            taskTypeAndTaskRunByTaskId() {
                return Object.fromEntries(
                    this.followedExecution?.taskRunList?.map((taskRun) => [
                        taskRun.taskId,
                        [this.taskType(taskRun), taskRun],
                    ]),
                );
            },
            currentTaskRunsLogIndicesByLevel() {
                return this.currentTaskRuns.reduce(
                    (currentTaskRunsLogIndicesByLevel, taskRun, taskRunIndex) => {
                        if (this.shouldDisplayLogs(taskRun)) {
                            const currentTaskRunLogs =
                                this.logsWithIndexByAttemptUid[
                                    this.attemptUid(
                                        taskRun.id,
                                        this.selectedAttemptNumberByTaskRunId[
                                            taskRun.id
                                        ],
                                    )
                                ];
                            currentTaskRunLogs?.forEach((log, logIndex) => {
                                currentTaskRunsLogIndicesByLevel[log.level] = [
                                    ...(currentTaskRunsLogIndicesByLevel?.[
                                        log.level
                                    ] ?? []),
                                    taskRunIndex + "/" + logIndex,
                                ];
                            });
                        }

                        return currentTaskRunsLogIndicesByLevel;
                    },
                    {},
                );
            },
            allLogIndicesByLevel() {
                const currentTaskRunsLogIndicesByLevel = {
                    ...this.currentTaskRunsLogIndicesByLevel,
                };
                return Object.entries(
                    this.childrenLogIndicesByLevelByChildUid,
                ).reduce(
                    (allLogIndicesByLevel, [logUid, childrenLogIndicesByLevel]) => {
                        Object.entries(childrenLogIndicesByLevel).forEach(
                            ([level, logIndices]) => {
                                allLogIndicesByLevel[level] = [
                                    ...(allLogIndicesByLevel?.[level] ?? []),
                                    ...logIndices.map(
                                        (logIndex) => logUid + "/" + logIndex,
                                    ),
                                ];
                            },
                        );

                        return allLogIndicesByLevel;
                    },
                    currentTaskRunsLogIndicesByLevel,
                );
            },
            levelOrLower() {
                return LogUtils.levelOrLower(this.level);
            },
            filteredLogs() {
                return this.rawLogs.filter((log) =>
                    this.levelOrLower.includes(log.level),
                );
            },
        },
        methods: {
            fileUrl(path) {
                return `${apiUrl()}/executions/${this.followedExecution.id}/file?path=${path}`;
            },
            async fetchAndStoreLogFileSize(path) {
                if (this.logFileSizeByPath[path] !== undefined) {
                    return;
                }

                const axiosResponse = await this.$http(
                    `${apiUrl()}/executions/${this.followedExecution.id}/file/metas?path=${path}`,
                    {
                        validateStatus: (status) =>
                            status === 200 || status === 404 || status === 422,
                    },
                );
                this.logFileSizeByPath[path] = Utils.humanFileSize(
                    axiosResponse.data.size,
                );
            },
            closeLogsSSE() {
                if (this.logsSSE) {
                    this.logsSSE.close();
                    this.logsSSE = undefined;
                }
            },
            toggleExpandCollapseAll() {
                if (this.shownAttemptsUid.length === 0) {
                    this.expandAll();
                } else {
                    this.collapseAll();
                }
            },
            autoExpandBasedOnSettings() {
                if (this.autoExpandTaskRunStates.length === 0) {
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

                    if (
                        this.taskRunId === taskRun.id ||
                        this.autoExpandTaskRunStates.includes(taskRun.state.current)
                    ) {
                        this.showAttempt(
                            this.attemptUid(
                                taskRun.id,
                                this.selectedAttemptNumberByTaskRunId[taskRun.id],
                            ),
                        );
                    }
                });
            },
            shouldDisplayLogs(taskRun) {
                const uid = this.attemptUid(
                    taskRun.id,
                    this.selectedAttemptNumberByTaskRunId[taskRun.id],
                );
                return (
                    (this.taskRunId || this.shownAttemptsUid.includes(uid)) &&
                    this.logsWithIndexByAttemptUid[uid]?.length > 0
                );
            },
            closeTargetExecutionSSE() {
                if (this.executionSSE) {
                    this.executionSSE.close();
                    this.executionSSE = undefined;
                }
            },
            followExecution(executionId) {
                this.closeTargetExecutionSSE();
                this.executionsStore
                    .followExecution({id: executionId, rawSSE: true})
                    .then((sse) => {
                        this.executionSSE = sse;
                        this.executionSSE.onmessage = (executionEvent) => {
                            const isEnd =
                                executionEvent &&
                                executionEvent.lastEventId === "end";
                            // we are receiving a first "fake" event to force initializing the connection: ignoring it
                            if (executionEvent.lastEventId !== "start") {
                                this.throttledExecutionUpdate(executionEvent);
                            }
                            if (isEnd) {
                                this.closeTargetExecutionSSE();
                                this.throttledExecutionUpdate.flush();
                            }
                        };
                    });
            },
            followLogs(executionId) {
                this.executionsStore.followLogs({id: executionId}).then((sse) => {
                    this.logsSSE = sse;

                    this.logsSSE.onmessage = (event) => {
                        // we are receiving a first "fake" event to force initializing the connection: ignoring it
                        if (event.lastEventId !== "start") {
                            this.logsBuffer = this.logsBuffer.concat(
                                JSON.parse(event.data),
                            );
                        }

                        clearTimeout(this.timeout);
                        this.timeout = setTimeout(() => {
                            this.timer = moment();
                            this.rawLogs = this._deduplicateLogs(this.rawLogs.concat(this.logsBuffer));
                            this.logsBuffer = [];
                            this.scrollToBottomFailedTask();
                        }, 100);

                        // force at least 1 logs refresh / 500ms
                        if (moment().diff(this.timer, "seconds") > 0.5) {
                            clearTimeout(this.timeout);
                            this.timer = moment();
                            this.rawLogs = this._deduplicateLogs(this.rawLogs.concat(this.logsBuffer));
                            this.logsBuffer = [];
                            this.scrollToBottomFailedTask();
                        }
                    };

                    this.logsSSE.onerror = (_) => {
                        this.coreStore.message = {
                            variant: "error",
                            title: this.$t("error"),
                            message: this.$t(
                                "something_went_wrong.loading_execution",
                            ),
                        };
                    };
                });
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },

            shouldDisplaySubflow(taskRunIndex, taskRun) {
                const subflowExecutionId = taskRun.outputs.executionId;
                const index = this.shownSubflowsIds.findIndex(
                    (item) => item.subflowExecutionId === subflowExecutionId,
                );
                if (index === -1) {
                    this.shownSubflowsIds.push({
                        subflowExecutionId: subflowExecutionId,
                        taskRunIndex: taskRunIndex,
                    });
                    return true;
                } else {
                    return (
                        this.shownSubflowsIds[index].taskRunIndex === taskRunIndex
                    );
                }
            },

            expandAll() {
                if (!this.followedExecution) {
                    setTimeout(() => this.expandAll(), 50);
                    return;
                }

                this.shownAttemptsUid = this.currentTaskRuns.map((taskRun) =>
                    this.attemptUid(
                        taskRun.id,
                        this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0,
                    ),
                );
                this.shownAttemptsUid.forEach((attemptUid) =>
                    this.logsScrollerRefs?.[attemptUid]?.[0]?.scrollToBottom(),
                );

                this.expandSubflows();
            },
            expandSubflows() {
                if (
                    this.currentTaskRuns.some((taskRun) => this.isSubflow(taskRun))
                ) {
                    const subflowLogsElements = Object.values(
                        this.subflowTaskRunDetailsRefs,
                    );
                    if (subflowLogsElements.length === 0) {
                        setTimeout(() => this.expandSubflows(), 50);
                    }

                    subflowLogsElements?.forEach((subflowLogs) =>
                        subflowLogs.expandAll(),
                    );
                }
            },
            collapseAll() {
                this.shownAttemptsUid = [];
            },
            attemptUid(taskRunId, attemptNumber) {
                return `${taskRunId}-${attemptNumber}`;
            },
            scrollToBottomFailedTask() {
                if (
                    this.autoExpandTaskRunStates.includes(
                        this.followedExecution?.state?.current,
                    )
                ) {
                    this.currentTaskRuns.forEach((taskRun) => {
                        if (
                            taskRun.state.current === State.FAILED ||
                            taskRun.state.current === State.RUNNING
                        ) {
                            const attemptNumber = taskRun.attempts
                                ? taskRun.attempts.length - 1
                                : (this.forcedAttemptNumber ?? 0);
                            if (
                                this.shownAttemptsUid.includes(
                                    `${taskRun.id}-${attemptNumber}`,
                                )
                            ) {
                                this.logsScrollerRefs?.[
                                    `${taskRun.id}-${attemptNumber}`
                                ]?.scrollToBottom();
                            }
                        }
                    });
                }
            },
            uniqueTaskRunDisplayFilter(currentTaskRun) {
                return !(this.taskRunId && this.taskRunId !== currentTaskRun.id);
            },
            loadLogs(executionId) {
                this.executionsStore
                    .loadLogs({
                        executionId,
                        params: {
                            minLevel: this.level,
                            taskId: this.taskRunById[this.taskRunId]?.taskId,
                        },
                    })
                    .then((logs) => {
                        // `loadLogs` returns a paginated response `{ results, total }`, and `rawLogs` must be an array of log lines.
                        this.rawLogs = logs?.results ?? logs ?? [];
                        // Discard any buffered SSE logs to prevent duplicates after the full REST fetch replaces `rawLogs`.
                        this.logsBuffer = [];
                    });
            },
            attempts(taskRun) {
                if (
                    this.followedExecution.state.current === State.RUNNING ||
                    this.forcedAttemptNumber === undefined
                ) {
                    return taskRun.attempts ?? [{state: taskRun.state}];
                }

                return taskRun.attempts
                    ? [taskRun.attempts[this.forcedAttemptNumber]]
                    : [];
            },
            showAttempt(attemptUid) {
                if (!this.shownAttemptsUid.includes(attemptUid)) {
                    this.shownAttemptsUid.push(attemptUid);
                }
            },
            toggleShowAttempt(attemptUid) {
                this.shownAttemptsUid = _xor(this.shownAttemptsUid, [attemptUid]);
            },
            swapDisplayedAttempt(event) {
                const {taskRunId, attemptNumber: newDisplayedAttemptNumber} =
                    event;
                this.shownAttemptsUid = this.shownAttemptsUid.map((attemptUid) =>
                    attemptUid.startsWith(`${taskRunId}-`)
                        ? this.attemptUid(taskRunId, newDisplayedAttemptNumber)
                        : attemptUid,
                );

                this.selectedAttemptNumberByTaskRunId[taskRunId] =
                    newDisplayedAttemptNumber;
            },
            taskType(taskRun) {
                if (!taskRun) return undefined;

                const task = FlowUtils.findTaskById(this.flow, taskRun?.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if (task === undefined && parentTaskRunId) {
                    return this.taskType(this.taskRunById[parentTaskRunId]);
                }
                return task ? task.type : undefined;
            },
            emitLogCursor(logCursor) {
                this.$emit("log-cursor", logCursor);
            },
            childLogIndicesByLevel(taskRunIndex, logIndex, logIndicesByLevel) {
                this.childrenLogIndicesByLevelByChildUid[
                    `${taskRunIndex}/${logIndex}`
                ] = logIndicesByLevel;
            },
            logsScrollerRef(el, ...ids) {
                ids.forEach((id) => (this.logsScrollerRefs[id] = el));
            },
            subflowTaskRunDetailsRef(el, id) {
                this.subflowTaskRunDetailsRefs[id] = el;
            },
            scrollToLog(logId) {
                const split = logId.split("/");
                this.$refs.taskRunScroller.scrollToItem(split[0]);
                this.logsScrollerRefs?.[split[0]]?.scrollToItem(split[1]);
                if (split.length > 2) {
                    this.subflowTaskRunDetailsRefs?.[
                        split[0] + "/" + split[1]
                    ]?.scrollToLog(split.slice(2).join("/"));
                }
            },

            _deduplicateLogs(logs) {
                const list = new Set();

                return logs.filter((log) => {
                    // Use the server-assigned index when present as it is the most stable unique identifier per log line per attempt.
                    const key = log.index !== undefined
                        ? `${log.taskRunId}-${log.attemptNumber}-${log.index}`
                        : `${log.taskRunId}-${log.attemptNumber}-${log.timestamp}-${log.message}`;

                    if (list.has(key)) return false;

                    list.add(key);

                    return true;
                });
            },
        },
        beforeUnmount() {
            this.closeLogsSSE();
        },
    };
</script>
<style scoped lang="scss">

.log-wrapper {
    :deep(
        > .vue-recycle-scroller__item-wrapper
            > .vue-recycle-scroller__item-view
            > div
    ) {
        padding-bottom: 1rem;
    }

    :deep(.line) {
        padding-left: 0;
    }

    .attempt-wrapper {
        background-color: var(--ks-background-input);
        margin-bottom: 0;
        border: 1px solid var(--ks-border-primary);

        :deep(.kel-card__body) {
            padding: 0;
        }

        .attempt-wrapper & {
            border-radius: 0.25rem;
        }

        tbody:last-child & {
            border-bottom: 1px solid var(--ks-border-primary);
        }

        .attempt-header {
            padding: 0 0.5rem 0.5rem;
            border-bottom: 1px solid var(--ks-border-primary);
        }

        .line {
            padding: 0.5rem;
        }
    }

    .output {
        margin-right: 5px;
    }

    pre {
        border: 1px solid var(--light);
        background-color: var(--ks-scrollbar-content);
        padding: 10px;
        margin-top: 5px;
        margin-bottom: var(--ks-font-size-lg);
    }

    .log-lines {
        transition: max-height 0.2s ease-out;
        max-height: 300px;

        &.single-line {
            max-height: calc(100vh - 250px);
        }

        .line {
            padding: 1rem;

            &.cursor {
                background-color: var(--ks-tooltip-border);
            }
        }
    }
}
</style>
