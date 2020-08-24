<template>
    <div v-if="execution" class="log-wrapper text-white text-monospace">
        <div v-for="taskItem in execution.taskRunList" :key="taskItem.id">
            <template v-if="(!task || task.id === taskItem.id) && taskItem.attempts">
                <div class="bg-dark attempt-wrapper">
                    <template v-for="(attempt, index) in taskItem.attempts">
                        <div
                            :id="`attempt-${index}-${attempt.state.startDate}`"
                            :key="`attempt-${index}-${attempt.state.startDate}`"
                        >
                            <!-- Tooltip -->
                            <b-tooltip
                                placement="top"
                                :target="`attempt-${index}-${attempt.state.startDate}`"
                                triggers="hover"
                            >
                                {{$t('from')}} : {{attempt.state.startDate | date('LLL:ss') }}
                                <br />
                                {{$t('to')}} : {{attempt.state.endDate | date('LLL:ss') }}
                                <br />
                                <br />
                                <clock />
                                {{$t('duration')}} : {{attempt.state.duration | humanizeDuration}}
                            </b-tooltip>

                            <div class="attempt">
                                <!-- Attempt Badge -->
                                <div>
                                    <b-badge
                                        :id="`attempt-badge-${taskItem.id}`"
                                        variant="primary mr-1"
                                    >{{$t('attempt')}} {{index + 1}}</b-badge>
                                </div>

                                <!-- Task id -->
                                <div class="task-id flex-grow-1">
                                    <span>{{taskItem.taskId | ellipsis(30)}}</span>
                                </div>

                                <!-- Dropdown menu with actions -->
                                <div>
                                    <b-dropdown size="sm" right variant="primary" no-caret>
                                        <template v-slot:button-content>
                                            <Menu />
                                        </template>
                                        <b-dropdown-item
                                            v-if="taskItem.outputs"
                                            @click="toggleShowOutput(taskItem)"
                                        >
                                            <eye />
                                            {{$t('toggle output')}}
                                        </b-dropdown-item>
                                        <b-dropdown-item>
                                            <restart
                                                :key="`restart-${index}-${attempt.state.startDate}`"
                                                :isButton="false"
                                                :execution="execution"
                                                :task="taskItem"
                                            />
                                        </b-dropdown-item>
                                    </b-dropdown>
                                </div>
                            </div>
                        </div>

                        <!-- Log lines -->
                        <template >
                            <template v-for="(log, i) in findLogs(taskItem.id, index)">
                                <log-line
                                    :level="level"
                                    :filter="filter"
                                    :log="log"
                                    :name="`${taskItem.id}-${index}-${i}`"
                                    :key="`${taskItem.id}-${index}-${i}`"
                                />
                            </template>
                        </template>
                    </template>
                </div>
            </template>
            <!-- Outputs -->
            <div v-if="showOutputs[taskItem.id] && taskItem.outputs" class="bg-dark mb-1 mt-1 outputs">
        <h6 class="p-2 mb-0">{{ $t('outputs') }}</h6>
        <pre class="bg-dark mb-0 mt-0" :key="taskItem.id">{{taskItem.outputs}}</pre>
        </div>
        <!-- <pre>{{execution}}</pre> -->
        <!-- <pre>{{logs}}</pre> -->
    </div></div>
</template>
<script>
import { mapState } from "vuex";
import LogLine from "./LogLine";
import Restart from "./Restart";
import Clock from "vue-material-design-icons/Clock";
import Eye from "vue-material-design-icons/Eye";
import Menu from "vue-material-design-icons/Menu";
export default {
    components: { LogLine, Restart, Clock, Eye, Menu },
    props: {
        level: {
            type: String,
            default: "INFO"
        },
        filter: {
            type: String,
            default: ""
        },
        taskRunId: {
            type: String,
        },
    },
    data() {
        return {
            showOutputs: {}
        };
    },
    watch: {
        level: function () {
            this.loadLogs()
        }
    },
    created() {
        this.loadLogs();
    },
    computed: {
        ...mapState("execution", ["execution", "task", "logs"])
    },
    methods: {
        toggleShowOutput(task) {
            this.showOutputs[task.id] = !this.showOutputs[task.id];
            this.$forceUpdate();
        },
        loadLogs() {
            let params = {minLevel: this.level};

            if (this.taskRunId) {
                params.taskRunId = this.taskRunId
            }

            if (this.execution && this.execution.state.current === "RUNNING") {
                this.$store
                    .dispatch("execution/followLogs", {
                        id: this.$route.params.id,
                        params: params
                    })
                    .then(sse => {
                        this.sse = sse;
                        this.$store.commit("execution/setLogs", []);

                        sse.subscribe("", (data) => {
                            this.$store.commit("execution/appendLogs", data);
                        });
                    });
            } else {
                this.$store.dispatch("execution/loadLogs", {
                    executionId: this.$route.params.id,
                    params: params
                });
            }
        },
        findLogs(taskRunId, attemptNumber) {
            return (this.logs || [])
                .filter(log => {
                    return log.taskRunId === taskRunId && log.attemptNumber === attemptNumber;
                })
        }
    },
    beforeDestroy() {
        if (this.sse) {
            this.sse.close();
            this.sse = undefined;
        }
    }
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";
.log-wrapper {
    border-radius: 5px;
    .line:nth-child(odd) {
        background-color: lighten($dark, 5%);
    }
    div.attempt {
        display: flex;
        font-family: $font-family-sans-serif;
        font-size: $font-size-base;
        margin-top: $paragraph-margin-bottom * 1.5;
        margin-bottom: 2px;
        .badge {
            font-size: $font-size-base;
            height: 100%;
            line-height: 100%;
            padding-bottom: 0;
        }
    }
    .attempt-wrapper {
        padding: 0.75rem;
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
    }.outputs {
    h6 {
      border-bottom: 1px solid $gray-600;
    }
    pre {
      border: 0;
    }
  }
}
</style>
