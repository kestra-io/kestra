<template>
    <div class="node-wrapper" :class="nodeClass">
        <div class="status-color" v-if="this.execution" :class="statusClass" />
        <div class="icon">
            <task-icon :cls="task.type" />
        </div>
        <div class="task-content">
            <div class="card-header">
                <div class="task-title">
                    <span>{{ task.id }}</span>
                </div>
            </div>
            <div v-if="task.state" class="status-wrapper">
                <status :status="state" />
            </div>
            <div class="info-wrapper">
                <span class="bottom">
                    <b-badge
                        v-if="this.execution"
                        variant="primary"
                        class="mr-1"
                        pill
                    >
                        {{ taskRuns.length }}
                    </b-badge>

                    <span v-if="duration">{{ $filters.humanizeDuration(duration) }}</span>
                </span>

                <b-btn-group>
                    <b-button
                        v-if="task.description"
                        class="node-action"
                    >
                        <markdown-tooltip :description="task.description" :id="hash" :modal="true" :title="task.id" />
                    </b-button>

                    <sub-flow-link
                        v-if="task.type === 'io.kestra.core.tasks.flows.Flow'"
                        :execution-id="taskRunsFlowExecutionId"
                        :namespace="task.namespace"
                        :flow-id="task.flowId"
                    />

                    <b-button
                        v-if="this.execution"
                        class="node-action"
                        :disabled="this.taskRuns.length === 0"
                        @click="onTaskSelect()"
                        v-b-modal="`modal-logs-${task.id}`"
                    >
                        <kicon :tooltip="$t('show task logs')">
                            <text-box-search />
                        </kicon>
                    </b-button>

                    <task-edit
                        class="node-action"
                        :modal-id="`modal-source-${hash}`"
                        :task="task"
                        :flow-id="flowId"
                        :namespace="namespace"
                    />
                </b-btn-group>
            </div>
        </div>



        <b-modal
            :id="`modal-logs-${task.id}`"
            :title="`Task ${task.id}`"
            hide-backdrop
            hide-footer
            modal-class="right"
            size="xl"
            v-if="execution"
        >
            <b-navbar toggleable="lg" type="light" variant="light">
                <b-navbar-toggle target="nav-collapse" />
                <b-collapse id="nav-collapse" is-nav>
                    <b-nav-form>
                        <search-field :router="false" @search="onSearch" class="mr-2" />
                        <log-level-selector :log-level="logLevel" @input="onLevelChange" />
                    </b-nav-form>
                </b-collapse>
            </b-navbar>
            <log-list
                :task-id="task.id"
                :filter="this.filter"
                :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                :level="logLevel"
                @follow="forwardEvent('follow', $event)"
            />
        </b-modal>
    </div>
</template>
<script>

    import {mapState} from "vuex";
    import Status from "../Status";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip";
    import State from "../../utils/state"
    import LogList from "../logs/LogList";
    import LogLevelSelector from "../../components/logs/LogLevelSelector";
    import SearchField from "../layout/SearchField";
    import TaskIcon from "../plugins/TaskIcon";
    import Kicon from "../Kicon"
    import TaskEdit from "override/components/flows/TaskEdit.vue";
    import SubFlowLink from "../flows/SubFlowLink"
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch";

    export default {
        components: {
            MarkdownTooltip,
            Status,
            TextBoxSearch,
            LogList,
            LogLevelSelector,
            SearchField,
            TaskIcon,
            Kicon,
            TaskEdit,
            SubFlowLink
        },
        props: {
            n: {
                type: Object,
                default: undefined
            },
            flowId: {
                type: String,
                required: true
            },
            namespace: {
                type: String,
                required: true
            },
            execution: {
                type: Object,
                default: undefined
            }
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            onTaskSelect() {
                this.$store.commit("execution/setTask", this.task);
            },
            onSearch(search) {
                this.filter = search
            },
            onLevelChange(level) {
                this.logLevel = level;
            }
        },
        data() {
            return {
                logLevel: "INFO",
                filter: undefined
            };
        },
        computed: {
            ...mapState("graph", ["node"]),
            ...mapState("auth", ["user"]),
            hash() {
                return this.n.uid.hashCode();
            },
            taskRuns() {
                return (this.execution && this.execution.taskRunList ? this.execution.taskRunList : [])
                    .filter(t => t.taskId === this.task.id)
            },
            taskRunsFlowExecutionId() {
                const task = this.taskRuns
                    .find(r => r.outputs && r.outputs.executionId)

                return task !== undefined ? task.outputs.executionId : undefined;
            },
            state() {
                if (!this.taskRuns) {
                    return  null;
                }

                if (this.taskRuns.length === 1) {
                    return this.taskRuns[0].state.current
                }

                const allStates = this.taskRuns.map(t => t.state.current);

                const SORT_STATUS = [
                    State.FAILED,
                    State.KILLED,
                    State.WARNING,
                    State.KILLING,
                    State.RESTARTED,
                    State.RUNNING,
                    State.CREATED,
                    State.SUCCESS
                ];

                // sorting based on SORT_STATUS array
                const result = allStates
                    .map((item) => {
                        const n = SORT_STATUS.indexOf(item[1]);
                        SORT_STATUS[n] = undefined;
                        return [n, item]
                    })
                    .sort()
                    .map((j) => j[1])

                return result[0];
            },
            duration() {
                console.log(this)
                return this.taskRuns ? this.taskRuns.reduce((inc, taskRun) => inc + this.$moment.duration(taskRun.state.duration).asMilliseconds() / 1000, 0) : null;
            },
            nodeClass() {
                return {
                    ["task-disabled"]: this.task.disabled,
                };
            },
            statusClass() {
                return {
                    ["bg-" + State.colorClass()[this.state]]: true,
                };
            },
            task() {
                return this.n.task;
            },
        }
    };
</script>
<style scoped lang="scss">
@import "../../styles/_variable.scss";

.node-wrapper {
    cursor: pointer;
    display: flex;
    width: 200px;
    background: var(--gray-100);

    .btn, .card-header {
        border-radius: 0 !important;
    }

    &.task-disabled {
        .card-header .task-title {
            text-decoration: line-through;
        }
    }

    > .icon {
        width: 35px;
        height: 51px;
        background: var(--white);
        border-right: 1px solid var(--table-border-color);
        position: relative;
    }

    .status-color {
        width: 10px;
        height: 51px;
        border-right: 1px solid var(--table-border-color);
    }

    .is-success {
        background-color: var(--green);
    }

    .is-running {
        background-color: var(--blue);
    }

    .is-failed {
        background-color: var(--red);
    }

    .bg-undefined {
        background-color: var(--gray-400);
    }

    .task-content {
        flex-grow: 1;
        width: 38px;

        .card-header {
            height: 25px;
            padding: 2px;
            margin: 0;
            border-bottom: 1px solid var(--table-border-color);
            flex: 1;
            flex-wrap: nowrap;

            background-color: var(--gray-200);
            .theme-dark & {
                background-color: var(--gray-300);
            }

            .icon-wrapper {
                display: inline-block;
                flex-shrink: 2;
            }

            .task-title {
                margin-left: 2px;
                display: inline-block;
                font-size: $font-size-sm;
                flex-grow: 1;
                overflow: hidden;
                text-overflow: ellipsis;
                max-width: 100%;
                white-space: nowrap;
            }

            :deep(.node-action) {
                flex-shrink: 2;
                padding-top: 18px;
                padding-right: 18px;
            }
        }

        .status-wrapper {
            margin: 10px;
        }
    }

    .card-wrapper {
        top: 50px;
        position: absolute;
    }

    .info-wrapper {
        display: flex;
        .bottom {
            padding: 4px 4px;
            color: $text-muted;
            font-size: $font-size-xs;
            flex-grow: 2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            position: relative;

            .badge {
                padding-bottom: $badge-padding-y;
                font-weight: bold;
                font-size: 100%;
                top: -4px;
            }
        }
    }

    .node-action {
        height: 26px;
        padding-top: 1px;
        padding-right: 5px;
        padding-left: 5px;
    }
}
</style>
