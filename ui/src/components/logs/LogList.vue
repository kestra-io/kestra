<template>
    <div v-if="execution" class="log-wrapper text-dark">
        <div v-for="currentTaskRun in execution.taskRunList" :key="currentTaskRun.id">
            <template
                v-if="displayTaskRun(currentTaskRun)"
            >
                <div class="bg-light attempt-wrapper">
                    <template v-for="(attempt, index) in attempts(currentTaskRun)">
                        <div
                            :key="`attempt-${index}-${currentTaskRun.id}`"
                        >
                            <b-tooltip
                                placement="top"
                                :target="`attempt-${index}-${currentTaskRun.id}`"
                                triggers="hover"
                            >
                                {{ $t("from") }} :
                                {{ attempt.state.startDate | date }}
                                <br>
                                {{ $t("to") }} :
                                {{ attempt.state.endDate | date }}
                                <br>
                                <br>
                                <clock />
                                {{ $t("duration") }} :
                                {{ attempt.state.duration | humanizeDuration }}
                            </b-tooltip>

                            <div class="attempt-header">
                                <div class="attempt-number mr-1">
                                    {{ $t("attempt") }} {{ index + 1 }}
                                </div>

                                <div class="task-id flex-grow-1" :id="`attempt-${index}-${currentTaskRun.id}`">
                                    <code>{{ currentTaskRun.taskId }}</code>
                                    <small v-if="currentTaskRun.value">
                                        {{ currentTaskRun.value }}
                                    </small>
                                </div>

                                <div class="task-duration">
                                    <small class="mr-1">
                                        <clock />
                                        <duration class="ml-2" :histories="attempt.state.histories" />
                                    </small>
                                </div>

                                <div class="task-status">
                                    <status
                                        class="status"
                                        :status="attempt.state.current"
                                        size="sm"
                                    />
                                </div>

                                <b-button-group>
                                    <sub-flow-link
                                        v-if="currentTaskRun.outputs && currentTaskRun.outputs.executionId"
                                        tab-execution="gantt"
                                        :execution-id="currentTaskRun.outputs.executionId"
                                    />

                                    <b-button
                                        :disabled="!(attempt.metrics && attempt.metrics.length > 0) "
                                        @click="
                                            toggleShowMetric(currentTaskRun, index)
                                        "
                                    >
                                        <kicon :tooltip="$t('toggle metrics')">
                                            <chart-areaspline />
                                        </kicon>
                                    </b-button>

                                    <b-button
                                        :disabled="!currentTaskRun.outputs || currentTaskRun.outputs.length ===0"
                                        @click="toggleShowOutput(currentTaskRun)"
                                    >
                                        <kicon :tooltip="$t('toggle output')">
                                            <location-exit />
                                        </kicon>
                                    </b-button>

                                    <restart
                                        :key="`restart-${index}-${attempt.state.startDate}`"
                                        :is-replay="true"
                                        :execution="execution"
                                        :task-run="currentTaskRun"
                                        :attempt-index="index"
                                        @follow="forwardEvent('follow', $event)"
                                    />

                                    <change-status
                                        :key="`change-status-${index}-${attempt.state.startDate}`"
                                        :execution="execution"
                                        :task-run="currentTaskRun"
                                        :attempt-index="index"
                                        @follow="forwardEvent('follow', $event)"
                                    />
                                </b-button-group>
                            </div>
                        </div>

                        <!-- Log lines -->
                        <template>
                            <template
                                v-for="(log, i) in findLogs(currentTaskRun.id, index)"
                            >
                                <log-line
                                    :level="level"
                                    :filter="filter"
                                    :log="log"
                                    :exclude-metas="excludeMetas"
                                    :name="`${currentTaskRun.id}-${index}-${i}`"
                                    :key="`${currentTaskRun.id}-${index}-${i}`"
                                />
                            </template>
                        </template>

                        <!-- Metrics -->
                        <metrics
                            v-if="showMetrics[currentTaskRun.id + '-' + index]"
                            :key="`metrics-${index}-${currentTaskRun.id}`"
                            class="table-unrounded mt-1"
                            :data="attempt.metrics"
                        />
                    </template>
                    <!-- Outputs -->
                    <vars
                        v-if="showOutputs[currentTaskRun.id]"
                        :title="$t('outputs')"
                        :execution="execution"
                        class="table-unrounded mt-1"
                        :key="currentTaskRun.id"
                        :data="currentTaskRun.outputs"
                    />
                </div>
            </template>
        </div>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import LogLine from "./LogLine";
    import Restart from "../executions/Restart";
    import ChangeStatus from "../executions/ChangeStatus";
    import Vars from "../executions/Vars";
    import Metrics from "../executions/Metrics";
    import Clock from "vue-material-design-icons/Clock";
    import LocationExit from "vue-material-design-icons/LocationExit";
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline";
    import State from "../../utils/state";
    import Status from "../Status";
    import SubFlowLink from "../flows/SubFlowLink"
    import Kicon from "../Kicon"
    import Duration from "../layout/Duration";

    export default {
        components: {
            LogLine,
            Restart,
            ChangeStatus,
            Clock,
            LocationExit,
            Vars,
            Metrics,
            ChartAreaspline,
            Status,
            SubFlowLink,
            Kicon,
            Duration
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
                fullscreen: false
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
            },
        },
        beforeDestroy() {
            if (this.sse) {
                this.sse.close();
                this.sse = undefined;
            }
        },
    };
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.log-wrapper {

    .line:nth-child(odd) {
        background-color: var(--gray-100);
    }

    .line:nth-child(even) {
        background-color: var(--gray-100-lighten-5);
    }

    .attempt-header {
        display: flex;
        font-family: $font-family-sans-serif;
        font-size: $font-size-base;
        margin-top: $paragraph-margin-bottom * 1.5;
        line-height: $btn-line-height;

        .theme-dark & {
            background-color: var(--gray-100);
        }

        .attempt-number {
            background: var(--gray-400);
            padding: $btn-padding-y $btn-padding-x;
            white-space: nowrap;
        }

        .task-id, .task-duration {
            padding: $btn-padding-y $btn-padding-x;
        }

        .task-id {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        small {
            color: var(--gray-500);
        }

        .task-duration {
            white-space: nowrap;

        }

        .task-status {
            button {
                height: 100%;
            }
        }

        button {
            border-radius: 0 !important;
        }
    }

    .attempt-wrapper {
        margin-bottom: $spacer;

        div:first-child > * {
            margin-top: 0;
        }
    }

    .output {
        margin-right: 5px;
    }

    pre {
        border: 1px solid var(--light);
        background-color: var(--gray-200);
        padding: 10px;
        margin-top: 5px;
        margin-bottom: 20px;
    }
}
</style>
