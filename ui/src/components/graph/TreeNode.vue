<template>
    <div class="node-wrapper" :class="nodeClass">
        <div class="status-color" v-if="!isFlow" :class="statusClass" />
        <div class="task-content">
            <div class="card-header">
                <div class="icon-wrapper">
                <!-- <img src=""/> -->
                </div>
                <div class="task-title">
                    <div :title="task.type" v-if="!childrenCount" class="task-item">
                        <console title />
                    </div>
                    <div v-else :title="$t('stream')" class="task-item">
                        <current-ac title />
                    </div>
                    <span>{{ task.id }}</span>
                </div>
            </div>
            <div v-if="task.state" class="status-wrapper">
                <status :status="state" />
            </div>
            <div class="info-wrapper">
                <span class="duration">
                    <span v-if="duration">{{ duration | humanizeDuration }}</span>
                </span>
                <b-btn-group>
                    <b-button
                        v-if="task.description"
                        :title="`${$t('description')}`"
                        class="node-action push"
                    >
                        <markdown-tooltip :id="hash" :description="task.description" />
                    </b-button>

                    <b-button
                        v-if="!isFlow && n.taskRun"
                        class="node-action"
                        :title="$t('show task logs')"
                        :disabled="!n.taskRun"
                        @click="onTaskRunSelect(n.taskRun)"
                        v-b-modal="`modal-logs-${n.taskRun.id}`"
                    >
                        <text-box-search :title="$t('show task logs')" />
                    </b-button>
                    <b-button
                        class="node-action"
                        size="sm"
                        v-b-modal="`modal-source-${hash}`"
                        :title="$t('show task source')"
                    >
                        <code-tags />
                    </b-button>
                    <sub-flow-link v-if="task.type === 'org.kestra.core.tasks.flows.Flow'" :execution-id="n.taskRun && n.taskRun.executionId" :namespace="task.namespace" :flow-id="task.flowId" />
                </b-btn-group>
            </div>
        </div>

        <b-modal
            :id="`modal-source-${hash}`"
            :title="`Task ${task.id}`"
            header-bg-variant="dark"
            header-text-variant="light"
            hide-backdrop
            modal-class="right"
            size="xl"
        >
            <template #modal-footer>
                <b-button @click="saveTask" v-if="canSave">
                    <content-save />
                    <span>{{ $t('save') }}</span>
                </b-button>
            </template>

            <editor @onSave="saveTask" v-model="taskYaml" lang="yaml" />
        </b-modal>

        <b-modal
            :id="`modal-logs-${n.taskRun.id}`"
            :title="`Task ${task.id}`"
            header-bg-variant="dark"
            header-text-variant="light"
            hide-backdrop
            hide-footer
            modal-class="right"
            size="xl"
            v-if="n.taskRun"
        >
            <log-list :task-run-id="n.taskRun.id" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" level="TRACE" />
        </b-modal>
    </div>
</template>
<script>
    import Console from "vue-material-design-icons/Console";
    import CodeTags from "vue-material-design-icons/CodeTags";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch";
    import CurrentAc from "vue-material-design-icons/CurrentAc";

    import SubFlowLink from "../flows/SubFlowLink"
    import {mapState} from "vuex";
    import Status from "../Status";
    import md5 from "md5";
    import YamlUtils from "../../utils/yamlUtils";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip";
    import State from "../../utils/state"
    import Editor from "../../components/inputs/Editor";
    import ContentSave from "vue-material-design-icons/ContentSave";
    import {canSaveFlowTemplate} from "../../utils/flowTemplate";
    import LogList from "../logs/LogList";

    export default {
        components: {
            MarkdownTooltip,
            Status,
            Console,
            CodeTags,
            TextBoxSearch,
            CurrentAc,
            SubFlowLink,
            Editor,
            ContentSave,
            LogList
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
            isFlow: {
                type: Boolean,
                default: false
            }
        },
        methods: {
            taskRunOutputToken(taskRun) {
                return md5(taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ""));
            },
            onFilterGroup() {
                this.$emit("onFilterGroup", this.task.id);
            },
            onTaskRunSelect(taskRun) {
                this.$store.commit("execution/setTaskRun", taskRun);
            },
            onSettings() {
                if (this.node) {
                    this.$store.dispatch("graph/setNode", undefined);
                } else {
                    this.$store.dispatch("graph/setNode", this.n);
                    this.$emit("onSettings");
                }
            },
            saveTask() {
                let task;
                try {
                    task = YamlUtils.parse(this.taskYaml);
                } catch (err) {
                    this.$toast().warning(
                        err.message,
                        this.$t("invalid yaml"),
                    );

                    return;
                }

                return this.$store
                    .dispatch("flow/updateFlowTask", {
                        flow: {
                            id: this.flowId,
                            namespace: this.namespace
                        },
                        task: task
                    })
                    .then((response) => {
                        this.$toast().saved(response.id);
                    })
            }
        },
        data() {
            return {
                taskYaml: undefined,
            };
        },
        created() {
            this.taskYaml = YamlUtils.stringify(this.n.task);
        },
        computed: {
            ...mapState("graph", ["node"]),
            ...mapState("auth", ["user"]),
            hasLogs() {
                return true;
            },
            hasOutputs() {
                return this.n.taskRun && this.n.taskRun.outputs;
            },
            attempts() {
                return this.n.taskRun && this.n.taskRun.attempts
                    ? this.n.taskRun.attempts
                    : [];
            },
            hash() {
                return this.n.uid.hashCode();
            },
            childrenCount() {
                return this.n.children ? this.n.children.length : 0;
            },
            state() {
                return this.n.taskRun ? this.n.taskRun.state.current : undefined;
            },
            duration() {
                console.log(this.n.taskRun)
                return this.n.taskRun ? this.n.taskRun.state.duration : null;
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
            value () {
                return this.n.taskRun && this.n.taskRun.value
            },
            canSave() {
                return canSaveFlowTemplate(true, this.user, {namespace:this.namespace}, "flow");
            }
        }
    };
</script>
<style scoped lang="scss">
@import "../../styles/_variable.scss";

.node-wrapper {
    cursor: pointer;
    display: flex;
    width: 180px;
    &.task-disabled {
        .card-header .task-title {
            text-decoration: line-through;
        }
    }

    .status-color {
        width: 10px;
        height: 48px;
        border: 0;
    }

    .is-success {
        background-color: $green;
    }

    .is-running {
        background-color: $blue;
    }

    .is-failed {
        background-color: $red;
    }

    .bg-undefined {
        background-color: $gray-400;
    }

    .task-content {
        flex-grow: 1;
        background-color: $white;
        width: 38px;

        .card-header {
            height: 23px;
            padding: 2px;
            margin: 0;
            border-bottom: 1px solid $gray-500;
            background: $gray-200;
            flex: 1;
            flex-wrap: nowrap;

            .icon-wrapper {
                display: inline-block;
                flex-shrink: 2;
                img {
                    width: 16px;
                    height: 16px;
                }
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

            .node-action {
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
        .duration {
            padding: 4px 4px;
            color: $text-muted;
            font-size: $font-size-xs;
            flex-grow: 2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
    }
    .push {
        margin-left: auto;
    }
    .pull {
        margin-right: auto;
    }


    .node-action {
        height: 25px;
        padding-top: 1px;
        padding-right: 5px;
        padding-left: 5px;
    }
    .task-item {
        flex: 1;
        display: inline;
        margin-right: 5px;
    }
}
</style>
