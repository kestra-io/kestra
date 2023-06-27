<template>
    <div v-if="execution" class="log-wrapper">
        <div v-for="currentTaskRun in currentTaskRuns" :key="currentTaskRun.id">
            <template
                v-if="displayTaskRun(currentTaskRun)"
            >
                <div class="bg-light attempt-wrapper">
                    <template
                        v-for="(attempt, index) in attempts(currentTaskRun)"
                        :key="`attempt-${index}-${currentTaskRun.id}`"
                    >
                        <div>
                            <div class="attempt-header">
                                <div class="attempt-number">
                                    {{ $t("attempt") }} {{ taskAttempt(index) + 1 }}
                                </div>
                                <div v-if="!hideOthersOnSelect">
                                    <el-button type="default" @click="() => toggleShowLogs(`${currentTaskRun.id}-${taskAttempt(index)}`)">
                                        <ChevronDown v-if="!showLogs.includes(`${currentTaskRun.id}-${taskAttempt(index)}`)" />
                                        <ChevronUp v-else />
                                    </el-button>
                                </div>
                                <div
                                    class="task-icon me-1"
                                >
                                    <task-icon
                                        :cls="taskIcon(currentTaskRun.taskId)"
                                        v-if="taskIcon(currentTaskRun.taskId)"
                                        only-icon
                                    />
                                </div>
                                <div class="task-id flex-grow-1" :id="`attempt-${index}-${currentTaskRun.id}`">
                                    <el-tooltip :persistent="false" transition="" :hide-after="0">
                                        <template #content>
                                            {{ $t("from") }} :
                                            {{ $filters.date(attempt.state.startDate) }}
                                            <br>
                                            {{ $t("to") }} :
                                            {{ $filters.date(attempt.state.endDate) }}
                                            <br>
                                            <clock />
                                            <strong>{{ $t("duration") }}:</strong>
                                            {{ $filters.humanizeDuration(attempt.state.duration) }}
                                        </template>
                                        <code>{{ currentTaskRun.taskId }}</code>
                                        <small v-if="currentTaskRun.value">
                                            {{ currentTaskRun.value }}
                                        </small>
                                    </el-tooltip>
                                </div>

                                <div class="task-duration">
                                    <small class="me-1">
                                        <clock />
                                        <duration class="ms-2" :histories="attempt.state.histories" />
                                    </small>
                                </div>

                                <div class="task-status">
                                    <status :status="attempt.state.current" />
                                </div>

                                <el-dropdown trigger="click" @visibleChange="onTaskSelect($event, currentTaskRun)">
                                    <el-button type="default">
                                        <DotsVertical title="" />
                                    </el-button>
                                    <template #dropdown>
                                        <el-dropdown-menu>
                                            <sub-flow-link
                                                v-if="currentTaskRun.outputs && currentTaskRun.outputs.executionId"
                                                component="el-dropdown-item"
                                                tab-execution="gantt"
                                                :execution-id="currentTaskRun.outputs.executionId"
                                            />

                                            <metrics />

                                            <outputs
                                                :outputs="currentTaskRun.outputs"
                                                :execution="execution"
                                            />

                                            <restart
                                                component="el-dropdown-item"
                                                :key="`restart-${index}-${attempt.state.startDate}`"
                                                is-replay
                                                tooltip-position="left"
                                                :execution="execution"
                                                :task-run="currentTaskRun"
                                                :attempt-index="index"
                                                @follow="forwardEvent('follow', $event)"
                                            />

                                            <change-status
                                                component="el-dropdown-item"
                                                :key="`change-status-${index}-${attempt.state.startDate}`"
                                                :execution="execution"
                                                :task-run="currentTaskRun"
                                                :attempt-index="index"
                                                @follow="forwardEvent('follow', $event)"
                                            />
                                            <task-edit
                                                :read-only="true"
                                                component="el-dropdown-item"
                                                :task-id="currentTaskRun.taskId"
                                                :section="SECTIONS.TASKS"
                                                :flow-id="execution.flowId"
                                                :namespace="execution.namespace"
                                                :revision="execution.flowRevision"
                                            />
                                            <component
                                                :is="'el-dropdown-item'"
                                                :icon="Download"
                                                @click="downloadContent(currentTaskRun.id)"
                                            >
                                                {{ $t("download") }}
                                            </component>
                                        </el-dropdown-menu>
                                    </template>
                                </el-dropdown>
                            </div>
                        </div>
                        <DynamicScroller
                            :items="indexedLogsList.filter((logline) => logline.taskRunId === currentTaskRun.id && logline.attemptNumber === taskAttempt(index))"
                            :min-item-size="50"
                            key-field="index"
                            class="log-lines"
                            :ref="`${currentTaskRun.id}-${taskAttempt(index)}`"
                            :class="hideOthersOnSelect || showLogs.includes(`${currentTaskRun.id}-${taskAttempt(index)}`) ? '' : 'hide-logs'"
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
                                    />
                                </DynamicScrollerItem>
                            </template>
                        </DynamicScroller>
                    </template>
                </div>
            </template>
        </div>
    </div>
</template>

<script setup>
    import {SECTIONS} from "../../utils/constants.js";
</script>

<script>
    import {mapState} from "vuex";
    import LogLine from "./LogLine.vue";
    import Restart from "../executions/Restart.vue";
    import ChangeStatus from "../executions/ChangeStatus.vue";
    import Metrics from "../executions/Metrics.vue";
    import Outputs from "../executions/Outputs.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import State from "../../utils/state";
    import Status from "../Status.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import TaskEdit from "../flows/TaskEdit.vue";
    import Duration from "../layout/Duration.vue";
    import TaskIcon from "../plugins/TaskIcon.vue";
    import _xor from "lodash/xor";
    import FlowUtils from "../../utils/flowUtils.js";
    import moment from "moment";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import {logDisplayTypes} from "../../utils/constants";


    export default {
        components: {
            LogLine,
            Restart,
            ChangeStatus,
            Clock,
            Metrics,
            Outputs,
            DotsVertical,
            ChevronDown,
            ChevronUp,
            Status,
            SubFlowLink,
            TaskEdit,
            Duration,
            TaskIcon,
        },
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
            taskId: {
                type: String,
                default: undefined,
            },
            fullScreenModal: {
                type: Boolean,
                default: false,
            },
            excludeMetas: {
                type: Array,
                default: () => [],
            },
            hideOthersOnSelect: {
                type: Boolean,
                default: false
            },
            attemptNumber: {
                type: Number,
                default: undefined
            },
            logsToOpenParent: {
                type: Array,
                default: undefined
            }
        },
        data() {
            return {
                showOutputs: {},
                showMetrics: {},
                fullscreen: false,
                page: 1,
                size: 100,
                followed: false,
                append: false,
                logsList: [],
                threshold: 200,
                showLogs: [],
                followLogs: [],
                logsTotal: 0,
                timer: undefined,
                timeout: undefined
            };
        },
        watch: {
            level: function () {
                this.page = 1;
                this.logsList = [];
                this.loadLogs();
            },
            execution: function () {
                if (this.execution && this.execution.state.current !== State.RUNNING && this.execution.state.current !== State.PAUSED) {
                    this.closeSSE();
                }
            },
            currentTaskRuns: function () {
                this.openTaskRun(this.logsToOpen);
            },
            logsToOpenParent: function () {
                this.openTaskRun(this.logsToOpenParent);
            }
        },
        mounted() {
            if (!this.fullScreenModal) {
                this.loadLogs();
            }
            if (this.logsToOpen.includes(this.execution.state.current)) {
                this.openTaskRun(this.logsToOpen);
            }
        },
        computed: {
            ...mapState("execution", ["execution", "taskRun", "task", "logs"]),
            ...mapState("flow", ["flow"]),
            currentTaskRuns() {
                return this.execution.taskRunList.filter(tr => this.taskRunId ? tr.id === this.taskRunId : true)
            },
            Download() {
                return Download
            },
            params() {
                let params = {minLevel: this.level};

                params.sort = "timestamp:asc"
                params.page = this.page;
                params.size = this.size;

                params.append = this.append

                if (this.taskRunId) {
                    params.taskRunId = this.taskRunId;

                    if (this.attemptNumber) {
                        params.attempt = this.attemptNumber;
                    }
                }

                if (this.taskId) {
                    params.taskId = this.taskId;
                }

                return params
            },
            indexedLogsList(){
                return this.logsList
                    .filter(e => this.filter === "" || (
                        e.message &&
                        e.message.toLowerCase().includes(this.filter)
                    ))
                    .map((e, index) => {
                        return {...e, index: index}
                    })
            },
            logsToOpen() {
                if (this.logsToOpenParent) {
                    return this.logsToOpenParent
                }
                switch(localStorage.getItem("logDisplay") || logDisplayTypes.DEFAULT){
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
        },
        methods: {
            openTaskRun(logsToOpen){
                this.currentTaskRuns.forEach((taskRun) => {
                    if (logsToOpen.length === 0) {
                        this.showLogs = []
                        return;
                    }
                    if (logsToOpen.includes(taskRun.state.current)) {
                        const attemptNumber = taskRun.attempts ? taskRun.attempts.length - 1 : 0
                        this.showLogs.push(`${taskRun.id}-${attemptNumber}`)
                        this?.$refs?.[`${taskRun.id}-${attemptNumber}`]?.[0]?.scrollToBottom();
                    }
                });
            },
            scrollToBottomFailedTask() {
                if (this.logsToOpen.includes(this.execution.state.current)) {
                    this.currentTaskRuns.forEach((taskRun) => {
                        if (taskRun.state.current === State.FAILED || taskRun.state.current === State.RUNNING) {
                            const attemptNumber = taskRun.attempts ? taskRun.attempts.length - 1 : (this.attemptNumber ?? 0)
                            if (this.showLogs.includes(`${taskRun.id}-${attemptNumber}`)) {
                                this?.$refs?.[`${taskRun.id}-${attemptNumber}`]?.[0]?.scrollToBottom();
                            }
                        }
                    });
                }
            },
            downloadContent(currentTaskRunId) {
                const params = this.params
                this.$store.dispatch("execution/downloadLogs", {
                    executionId: this.execution.id,
                    params: params
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
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.execution.id}-${currentTaskRunId}.log`
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            displayTaskRun(currentTaskRun) {
                if (!this.hideOthersOnSelect) {
                    return true;
                }

                if (this.taskRun && this.taskRun.id !== currentTaskRun.id) {
                    return false;
                }

                if (this.task && this.task.id !== currentTaskRun.taskId) {
                    return false;
                }

                return true;
            },
            taskIcon(taskId) {
                let findTaskById = FlowUtils.findTaskById(this.flow, taskId);
                return findTaskById ? findTaskById.type : undefined;
            },
            loadLogs() {
                const params = this.params

                if (this.execution && this.execution.state.current === State.RUNNING) {
                    this.append = true
                    this.$store
                        .dispatch("execution/followLogs", {
                            id: this.$route.params.id,
                            params: params,
                        })
                        .then((sse) => {
                            const self = this;
                            this.sse = sse;
                            this.followed = true;
                            this.$store.commit("execution/resetLogs");

                            this.timer = moment()
                            this.sse.onmessage = (event) => {
                                if (event && event.lastEventId === "end") {
                                    self.closeSSE();
                                }
                                this.$store.commit("execution/appendFollowedLogs", JSON.parse(event.data));
                                this.followLogs = this.followLogs.concat(JSON.parse(event.data));

                                clearTimeout(this.timeout);
                                this.timeout = setTimeout(() => {
                                    this.timer = moment()
                                    this.logsList = JSON.parse(JSON.stringify(this.followLogs))
                                    this.scrollToBottomFailedTask();
                                }, 100);
                                if(moment().diff(this.timer, "seconds") > 0.5){
                                    clearTimeout(this.timeout);
                                    this.timer = moment()
                                    this.logsList = JSON.parse(JSON.stringify(this.followLogs))
                                    this.scrollToBottomFailedTask();
                                }
                            }
                        });
                } else {
                    this.$store.dispatch("execution/loadLogs", {
                        executionId: this.$route.params.id,
                        params: params,
                    }).then(r => {
                        this.logsList = r
                        this.logsTotal = r.total
                    });
                    this.closeSSE();
                }
            },
            closeSSE() {
                if (this.sse) {
                    this.sse.close();
                    this.sse = undefined;
                    this.logsList = this.followLogs
                }
            },
            attempts(taskRun) {
                return this.execution.state.current === State.RUNNING || !this.attemptNumber ? taskRun.attempts : [taskRun.attempts[this.attemptNumber]] ;
            },
            onTaskSelect(dropdownVisible, task) {
                if (dropdownVisible && this.taskRun?.id !== task.id) {
                    this.$store.commit("execution/setTaskRun", task);
                }
            },
            toggleShowLogs(currentTaskRunId) {
                this.showLogs = _xor(this.showLogs, [currentTaskRunId])
            },
            taskAttempt(index) {
                return this.attemptNumber || index
            }
        },
        beforeUnmount() {
            if (this.sse) {
                this.sse.close();
                this.sse = undefined;
            }
        },
    };
</script>
<style lang="scss" scoped>
    .log-wrapper {
        .line:nth-child(odd) {
            background-color: var(--bs-gray-100);
        }

        .line:nth-child(even) {
            background-color: var(--bs-gray-100-lighten-5);
        }

        .attempt-header {
            display: flex;

            html.dark & {
                background-color: var(--bs-gray-100);
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
                padding: .375rem .75rem;
            }

            .task-id {
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .task-icon {
                width: 36px;
                background: var(--bs-white);
                padding: 6px;
            }

            small {
                color: var(--bs-gray-500);
            }

            .task-duration small {
                white-space: nowrap;

                html.dark & {
                    color: var(--bs-gray-600);
                }
            }

            .el-dropdown {
                > .el-button {
                    border: 0;
                }
            }

            .task-status {
                button {
                    border: 0;
                }
            }

            :deep(button.el-button) {
                border-radius: 0 !important;
                height: 100%;
            }
        }

        .attempt-wrapper {
            margin-bottom: var(--spacer);

            div:first-child > * {
                margin-top: 0;
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
            overflow-y: scroll;
            transition: max-height 0.2s ease-out;


            &::-webkit-scrollbar {
                width: 5px;
            }

            &:hover::-webkit-scrollbar {
                width: 10px;
            }

            &::-webkit-scrollbar-track {
                background: var(--bs-gray-500);
            }

            &::-webkit-scrollbar-thumb {
                background: var(--bs-primary);
            }
        }

        .hide-logs {
            max-height: 0;
        }

        button {
            border: none
        }
    }
</style>
