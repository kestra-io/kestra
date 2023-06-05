<template>
    <tree-node
        :id="task.id"
        :type="task.type"
        :disabled="task.disabled"
        :state="this.state"
    >
        <template #content>
            <div v-if="task.state" class="status-wrapper">
                <status :status="state" />
            </div>
        </template>
        <template #info>
            <span class="bottom">
                <el-tag
                    v-if="this.execution"
                    type="info"
                    class="me-1"
                    size="small"
                    round
                    disable-transitions
                >
                    {{ taskRuns.length }}
                </el-tag>

                <span v-if="execution && histories"><duration :histories="histories" /></span>
            </span>

            <el-drawer
                v-if="isNewErrorOpen"
                v-model="isNewErrorOpen"
                title="Add an error handler"
                destroy-on-close
                size=""
                :append-to-body="true"
            >
                <el-form label-position="top">
                    <task-editor
                        :section="SECTIONS.TASKS"
                        @update:model-value="onUpdateNewError( $event)"
                    />
                </el-form>
                <template #footer>
                    <el-button :icon="ContentSave" @click="onSaveNewError()" :disabled="!newError" type="primary">
                        {{ $t("save") }}
                    </el-button>
                </template>
            </el-drawer>

            <el-button-group>
                <el-button
                    v-if="task.description"
                    class="node-action"
                    size="small"
                    @click="$refs.descriptionTask.open()"
                >
                    <markdown-tooltip ref="descriptionTask" :description="task.description" :id="hash" :title="task.id" />
                </el-button>

                <sub-flow-link
                    v-if="task.type === 'io.kestra.core.tasks.flows.Flow'"
                    :execution-id="taskRunsFlowExecutionId"
                    size="small"
                    :namespace="task.namespace"
                    :flow-id="task.flowId"
                />

                <el-tooltip v-if="isFlowable && !this.execution && !isReadOnly && isAllowedEdit" content="Handle errors" transition="" :hide-after="0" :persistent="false">
                    <el-button
                        class="node-action"
                        size="small"
                        @click="isNewErrorOpen = true"
                        :icon="AlertCircleOutline"
                    />
                </el-tooltip>

                <el-tooltip v-if="!this.execution && !isReadOnly && isAllowedEdit" content="Delete" transition="" :hide-after="0" :persistent="false">
                    <el-button
                        class="node-action"
                        size="small"
                        @click="forwardEvent('delete', {id: this.task.id, section: SECTIONS.TASKS})"
                        :icon="Delete"
                    />
                </el-tooltip>

                <el-tooltip v-if="this.execution" :content="$t('show task logs')" :persistent="false" transition="" :hide-after="0">
                    <el-button
                        class="node-action"
                        :disabled="this.taskRuns.length === 0"
                        size="small"
                        @click="onTaskSelect()"
                    >
                        <text-box-search />
                    </el-button>
                </el-tooltip>

                <el-tooltip content="Edit task" :persistent="false" transition="" :hide-after="0">
                    <task-edit
                        v-if="!this.isReadOnly && isAllowedEdit"
                        class="node-action"
                        :section="SECTIONS.TASKS"
                        :task="task"
                        :flow-id="flowId"
                        size="small"
                        :namespace="namespace"
                        :revision="revision"
                        :emit-only="true"
                        @update:task="forwardEvent('edit', $event)"
                    />
                </el-tooltip>
            </el-button-group>
            <el-drawer
                v-if="isOpen && execution"
                v-model="isOpen"
                :title="`Task ${task.id}`"
                destroy-on-close
                size=""
                :append-to-body="true"
            >
                <collapse>
                    <el-form-item>
                        <search-field :router="false" @search="onSearch" class="me-2" />
                    </el-form-item>
                    <el-form-item>
                        <log-level-selector :value="logLevel" @update:model-value="onLevelChange" />
                    </el-form-item>
                </collapse>
                <log-list
                    :task-id="task.id"
                    :filter="this.filter"
                    :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
                    :level="logLevel"
                    @follow="forwardEvent('follow', $event)"
                    :hide-others-on-select="true"
                />
            </el-drawer>
        </template>
    </tree-node>
</template>

<script setup>
    import {SECTIONS} from "../../utils/constants.js";
</script>

<script>
    import {mapState} from "vuex";
    import Status from "../Status.vue";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip.vue";
    import State from "../../utils/state"
    import LogList from "../logs/LogList.vue";
    import LogLevelSelector from "../../components/logs/LogLevelSelector.vue";
    import SearchField from "../layout/SearchField.vue";
    import TaskEdit from "../flows/TaskEdit.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue"
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import Collapse from "../layout/Collapse.vue";
    import Duration from "../layout/Duration.vue";
    import TreeNode from "./TreeNode.vue";
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import taskEditor from "../flows/TaskEditor.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";

    export default {
        components: {
            MarkdownTooltip,
            Status,
            TextBoxSearch,
            LogList,
            LogLevelSelector,
            SearchField,
            TaskEdit,
            SubFlowLink,
            Collapse,
            Duration,
            TreeNode,
            taskEditor
        },
        emits: ["follow", "edit", "delete","addFlowableError"],
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
            revision: {
                type: Number,
                default: undefined
            },
            isFlowable: {
                type: Boolean,
                required: true
            },
            isReadOnly: {
                type: Boolean,
                required: true
            },
            isAllowedEdit: {
                type: Boolean,
                required: true
            },
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            onTaskSelect() {
                this.$store.commit("execution/setTask", this.task);
                this.isOpen = true;
            },
            onSearch(search) {
                this.filter = search
            },
            onLevelChange(level) {
                this.logLevel = level;
            },
            onUpdateNewError(event){
                this.newError = event;
            },
            onSaveNewError(){
                this.forwardEvent("addFlowableError", {taskId: this.task.id, error: this.newError})
                this.isNewErrorOpen = false;
            }
        },
        data() {
            return {
                logLevel: localStorage.getItem("defaultLogLevel") || "INFO",
                filter: undefined,
                isOpen: false,
                isNewErrorOpen: false,
                newError: null
            };
        },
        computed: {
            ContentSave() {
                return ContentSave
            },
            AlertCircleOutline() {
                return AlertCircleOutline;
            },
            Delete() {
                return Delete;
            },
            ...mapState("graph", ["node"]),
            ...mapState("auth", ["user"]),
            ...mapState("execution", ["execution"]),
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
                    State.RUNNING,
                    State.SUCCESS,
                    State.RESTARTED,
                    State.CREATED,
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
            histories() {
                if (!this.taskRuns) {
                    return undefined;
                }

                const max = Math.max(...this.taskRuns
                    .filter(value => value.state.histories && value.state.histories.length > 0)
                    .map(value => new Date(value.state.histories[value.state.histories.length -1].date).getTime()));

                const duration = this.taskRuns
                    .reduce((inc, taskRun) => inc + this.$moment.duration(taskRun.state.duration).asMilliseconds() / 1000, 0);

                return [
                    {date: this.$moment(max).subtract(duration, "second"), state: "CREATED"},
                    {date: this.$moment(max), state: this.state}
                ]
            },
            task() {
                return this.n.task;
            },
        },
    };
</script>
<style scoped lang="scss">
    .node-action {
        height: 28px;
        padding-top: 1px;
        padding-right: 5px;
        padding-left: 5px;
    }

    .status-wrapper {
        margin: 10px;
    }

    .info-wrapper {
        .bottom {
            padding: 4px 4px;
            color: var(--bs-body-color);
            opacity: 0.7;
            font-size: var(--font-size-xs);
            flex-grow: 2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            position: relative;
        }
    }
</style>
