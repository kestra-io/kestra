<template>
    <div class="node-wrapper">
        <div class="status-color" v-if="n.taskRun" :class="contentCls" />
        <div class="task-content">
            <div class="card-header">
                <div class="icon-wrapper">
                <!-- <img src=""/> -->
                </div>
                <div class="task-title">
                    <span>{{ task.id | ellipsis(18) }}</span>
                </div>
            <!-- <menu-open class="node-action" @click="onSettings" /> -->
            </div>
            <div v-if="task.state" class="status-wrapper">
                <status :status="state" />
            </div>
            <div class="info-wrapper">
                <div :title="task.type" v-if="!childrenCount" class="pull task-item">
                    <console title />
                </div>
                <div v-else :title="$t('stream')" class="pull task-item">
                    <current-ac title />
                </div>
                <b-button
                    v-if="task.description"
                    :title="`${$t('description')}`"
                    class="node-action push"
                >
                    <markdown-tooltip :id="task.id" :description="task.description" />
                </b-button>

                <b-button
                    v-if="childrenCount"
                    :title="`${$t('display direct sub tasks count')} : ${childrenCount}`"
                    class="node-action push"
                    @click="onFilterGroup"
                >
                    <graph title />
                </b-button>

                <b-button
                    v-if="!isFlow"
                    :disabled="!hasLogs"
                    class="node-action"
                    :title="$t('show task logs')"
                >
                    <router-link
                        :title="$t('show task logs')"
                        v-if="hasLogs"
                        class="btn-secondary"
                        :to="{name:'executionEdit', params: $route.params, query: {tab:'logs', search: task.id}}"
                    >
                        <format-list-checks title />
                    </router-link>
                    <format-list-checks v-else title />
                </b-button>
                <b-button
                    v-if="!isFlow"
                    :disabled="!hasOutputs"
                    class="node-action"
                    :title="$t('show task outputs')"
                >
                    <router-link
                        v-if="hasOutputs"
                        class="btn-secondary"
                        :title="$t('show task outputs')"
                        :to="{name:'executionEdit', params: $route.params, query: {tab:'execution-output', search: taskRunOutputToken(n.taskRun)}}"
                    >
                        <location-exit title />
                    </router-link>
                    <location-exit v-else title />
                </b-button>
                <b-button
                    class="node-action"
                    size="sm"
                    v-b-modal="`modal-${hash}`"
                    :title="$t('show task source')"
                >
                    <code-tags />
                </b-button>
                <sub-flow-link v-if="task.type === 'org.kestra.core.tasks.flows.Flow'" :execution-id="n.taskRun && n.taskRun.executionId" :namespace="task.namespace" :flow-id="task.flowId" />
            </div>
        </div>

        <b-modal
            :id="`modal-${hash}`"
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
    </div>
</template>
<script>
    import Console from "vue-material-design-icons/Console";
    import Graph from "vue-material-design-icons/Graph";
    import CodeTags from "vue-material-design-icons/CodeTags";
    import FormatListChecks from "vue-material-design-icons/FormatListChecks";
    import LocationExit from "vue-material-design-icons/LocationExit";
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

    export default {
        components: {
            MarkdownTooltip,
            Status,
            Console,
            Graph,
            CodeTags,
            FormatListChecks,
            LocationExit,
            CurrentAc,
            SubFlowLink,
            Editor,
            ContentSave,
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
            d(task) {
                console.log(task)
            },
            taskRunOutputToken(taskRun) {
                return md5(taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ""));
            },
            onFilterGroup() {
                this.$emit("onFilterGroup", this.task.id);
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
                // @TODO
                return true;
            // return (
            //     this.attempts.filter(attempt => attempt.logs.length).length > 0
            // );
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
                return this.task.id.hashCode();
            },
            childrenCount() {
                return this.n.children ? this.n.children.length : 0;
            },

            state() {
                return this.n.taskRun ? this.n.taskRun.state.current : State.SUCCESS;
            },
            contentCls() {
                return {
                    "is-success": ![State.RUNNING, State.FAILED].includes(this.state),
                    "is-running": this.state === State.RUNNING,
                    "is-failed": this.state === State.FAILED
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
.wrapper.is-container {
    border: 2px dashed $purple;
    border-radius: 2px;
}
.node-wrapper {
    cursor: pointer;
    display: flex;

    .status-color {
        width: 4px;
        height: 55px;
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

    .task-content {
        flex-grow: 1;
        background-color: $white;

        .card-header {
            height: 30px;
            padding: 2px;
            margin: 0px;
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
                margin-left: 5px;
                display: inline-block;
                font-size: $font-size-sm;
                flex-grow: 1;
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
        justify-content: right;
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
        margin-left: 5px;
        margin-bottom: 5px;
    }
}
</style>
