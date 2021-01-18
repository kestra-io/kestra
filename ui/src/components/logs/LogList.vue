<template>
    <div v-if="execution" class="log-wrapper text-white">
        <div v-for="currentTaskRun in execution.taskRunList" :key="currentTaskRun.id">
            <template
                v-if="displayTaskRun(currentTaskRun)"
            >
                <div class="bg-dark attempt-wrapper">
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

                                <div class="task-id">
                                    <small class="mr-1">
                                        <clock />
                                        {{ attempt.state.duration | humanizeDuration }}
                                    </small>
                                </div>

                                <div class="task-status">
                                    <status
                                        class="status"
                                        :status="currentTaskRun.state.current"
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
                                        :is-button-group="true"
                                        :execution="execution"
                                        :task="currentTaskRun"
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
                        <vars
                            v-if="showMetrics[currentTaskRun.id + '-' + index]"
                            :title="$t('metrics')"
                            :execution="execution"
                            :key="`metrics-${index}-${currentTaskRun.id}`"
                            :data="convertMetric(attempt.metrics)"
                        />
                    </template>
                    <!-- Outputs -->
                    <vars
                        v-if="showOutputs[currentTaskRun.id]"
                        :title="$t('outputs')"
                        :execution="execution"
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
    import humanizeDuration from "humanize-duration";
    import LogLine from "./LogLine";
    import Restart from "../executions/Restart";
    import Vars from "../executions/Vars";
    import Clock from "vue-material-design-icons/Clock";
    import LocationExit from "vue-material-design-icons/LocationExit";
    import ChartAreaspline from "vue-material-design-icons/ChartAreaspline";
    import State from "../../utils/state";
    import Status from "../Status";
    import SubFlowLink from "../flows/SubFlowLink"
    import Kicon from "../Kicon"

    export default {
        components: {
            LogLine,
            Restart,
            Clock,
            LocationExit,
            Vars,
            ChartAreaspline,
            Status,
            SubFlowLink,
            Kicon,
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
            excludeMetas: {
                type: Array,
                default: () => [],
            },
        },
        data() {
            return {
                showOutputs: {},
                showMetrics: {},
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
            this.loadLogs();
        },
        computed: {
            ...mapState("execution", ["execution", "taskRun", "task", "logs"]),
        },
        methods: {
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
            convertMetric(metrics) {
                return (metrics || []).reduce((accumulator, r) => {
                    accumulator[r.name] =
                        r.type === "timer"
                            ? humanizeDuration(parseInt(r.value * 1000))
                            : r.value;
                    return accumulator;
                }, Object.create(null));
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
        background-color: $gray-800;
    }
    .line:nth-child(even) {
        background-color: lighten($gray-800, 5%);
    }

    .attempt-header {
        display: flex;
        font-family: $font-family-sans-serif;
        font-size: $font-size-base;
        margin-top: $paragraph-margin-bottom * 1.5;
        line-height: $btn-line-height;

        .attempt-number {
            background: $primary;
            padding: $btn-padding-y $btn-padding-x;
        }

        .task-id {
            padding: $btn-padding-y $btn-padding-x;
        }

        .task-status {
            button {
                height: 100%;
                border-radius: 0 !important;
            }
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
        border: 1px solid $light;
        background-color: $gray-200;
        padding: 10px;
        margin-top: 5px;
        margin-bottom: 20px;
    }
}
</style>
