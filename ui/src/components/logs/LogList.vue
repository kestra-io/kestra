<template>
    <div v-if="execution" class="log-wrapper">
        <div v-for="currentTaskRun in execution.taskRunList" :key="currentTaskRun.id">
            <template
                v-if="displayTaskRun(currentTaskRun)"
            >
                <div class="bg-light attempt-wrapper">
                    <template v-for="(attempt, index) in attempts(currentTaskRun)" :key="`attempt-${index}-${currentTaskRun.id}`">
                        <div>
                            <div class="attempt-header">
                                <div class="attempt-number me-1">
                                    {{ $t("attempt") }} {{ index + 1 }}
                                </div>
                                <div class="task-icon me-1" v-loading="this.tasksIcons[currentTaskRun.id] === undefined">
                                    <task-icon :cls="tasksIcons[currentTaskRun.id]" only-icon />
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

                                <el-dropdown trigger="click">
                                    <el-button type="primary">
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

                                            <metrics :metrics="attempt.metrics" />

                                            <outputs
                                                :outputs="currentTaskRun.outputs"
                                                :execution="execution"
                                            />

                                            <restart
                                                component="el-dropdown-item"
                                                :key="`restart-${index}-${attempt.state.startDate}`"
                                                :is-replay="true"
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
                                                :flow-id="execution.flowId"
                                                :namespace="execution.namespace"
                                                :revision="execution.flowRevision"
                                            />
                                        </el-dropdown-menu>
                                    </template>
                                </el-dropdown>
                            </div>
                        </div>

                        <template
                            v-for="(log, i) in findLogs(currentTaskRun.id, index)"
                            :key="`${currentTaskRun.id}-${index}-${i}`"
                        >
                            <log-line
                                :level="level"
                                :filter="filter"
                                :log="log"
                                :exclude-metas="excludeMetas"
                                :name="`${currentTaskRun.id}-${index}-${i}`"
                            />
                        </template>
                    </template>
                </div>
            </template>
        </div>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import LogLine from "./LogLine.vue";
    import Restart from "../executions/Restart.vue";
    import ChangeStatus from "../executions/ChangeStatus.vue";
    import Metrics from "../executions/Metrics.vue";
    import Outputs from "../executions/Outputs.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import State from "../../utils/state";
    import Status from "../Status.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import TaskEdit from "../flows/TaskEdit.vue";
    import Duration from "../layout/Duration.vue";
    import TaskIcon from "../plugins/TaskIcon.vue";

    export default {
        components: {
            LogLine,
            Restart,
            ChangeStatus,
            Clock,
            Metrics,
            Outputs,
            DotsVertical,
            Status,
            SubFlowLink,
            TaskEdit,
            Duration,
            TaskIcon
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
        },
        data() {
            return {
                showOutputs: {},
                showMetrics: {},
                fullscreen: false,
                tasksIcons: {}
            };
        },
        watch: {
            level: function () {
                this.loadLogs();
            },
            execution: function() {
                if (this.execution && this.execution.state.current !== State.RUNNING) {
                    this.closeSSE();
                }
            },
        },
        created() {
            if (!this.fullScreenModal) {
                this.loadLogs();
            }
            if (this.execution){
                for(let currentTaskRun of this.execution.taskRunList){
                    this.$store.dispatch("flow/loadTask", {
                        namespace: currentTaskRun.namespace,
                        id: currentTaskRun.flowId,
                        taskId: currentTaskRun.taskId,
                        revision: currentTaskRun.revision
                    }).then(value => {
                        this.tasksIcons[currentTaskRun.id] = value.type
                    })
                }
            }
        },
        computed: {
            ...mapState("execution", ["execution", "taskRun", "task", "logs"]),
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            displayTaskRun(currentTaskRun) {
                if (this.taskRun && this.taskRun.id !== currentTaskRun.id) {
                    return false;
                }

                if (this.task && this.task.id !== currentTaskRun.taskId) {
                    return false;
                }
                return  true;
            },
            toggleShowOutput(taskRun) {
                this.showOutputs[taskRun.id] = !this.showOutputs[taskRun.id];
                this.$forceUpdate();
            },
            toggleShowMetric(taskRun, index) {
                this.showMetrics[taskRun.id + "-" + index] = !this.showMetrics[
                    taskRun.id + "-" + index
                ];
                this.$forceUpdate();
            },
            loadLogs() {
                let params = {minLevel: this.level};

                if (this.taskRunId) {
                    params.taskRunId = this.taskRunId;
                }

                if (this.taskId) {
                    params.taskId = this.taskId;
                }

                if (this.execution && this.execution.state.current === State.RUNNING) {
                    this.$store
                        .dispatch("execution/followLogs", {
                            id: this.$route.params.id,
                            params: params,
                        })
                        .then((sse) => {
                            const self = this;
                            this.sse = sse;
                            this.$store.commit("execution/setLogs", []);

                            this.sse.onmessage = (event) => {
                                if (event && event.lastEventId === "end") {
                                    self.closeSSE();
                                }

                                this.$store.commit("execution/appendLogs", JSON.parse(event.data));
                            }
                        });
                } else {
                    this.$store.dispatch("execution/loadLogs", {
                        executionId: this.$route.params.id,
                        params: params,
                    });
                    this.closeSSE();
                }
            },
            closeSSE() {
                if (this.sse) {
                    this.sse.close();
                    this.sse = undefined;
                }
            },
            attempts(taskRun) {
                return taskRun.attempts || [{
                    state: taskRun.state
                }];
            },
            findLogs(taskRunId, attemptNumber) {
                return (this.logs || []).filter((log) => {
                    return (
                        log.taskRunId === taskRunId &&
                        log.attemptNumber === attemptNumber
                    );
                });
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

            .task-duration {
                white-space: nowrap;

            }

            .task-status {
                button {

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
    }
</style>
