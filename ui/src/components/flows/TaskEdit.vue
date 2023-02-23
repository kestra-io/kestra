<template>
    <component
        :is="component"
        :icon="CodeTags"
        @click="onShow"
    >
        <span v-if="component !== 'el-button'">{{ $t('show task source') }}</span>
        <el-drawer
            v-if="isModalOpen"
            v-model="isModalOpen"
            destroy-on-close
            lock-scroll
            size=""
            :append-to-body="true"
        >
            <template #header>
                <code>{{ taskId || task.id }}</code>
            </template>
            <template #footer>
                <div v-loading="isLoading">
                    <el-button :icon="ContentSave" @click="saveTask" v-if="canSave && !isReadOnly" type="primary">
                        {{ $t('save') }}
                    </el-button>
                    <el-alert show-icon :closable="false" class="mb-0 mt-3" v-if="revision && isReadOnly" type="warning">
                        <strong>{{ $t('seeing old revision', {revision: revision}) }}</strong>
                    </el-alert>
                </div>
            </template>

            <el-tabs v-if="taskYaml" v-model="activeTabs">
                <el-tab-pane name="source">
                    <template #label>
                        <span>{{ $t('source') }}</span>
                    </template>
                    <editor
                        v-if="taskYaml"
                        :read-only="isReadOnly"
                        ref="editor"
                        @save="saveTask"
                        v-model="taskYaml"
                        :schema-type="mapSectionWithSchema()"
                        :full-height="false"
                        :navbar="false"
                        lang="yaml"
                    />
                </el-tab-pane>
                <el-tab-pane name="form">
                    <template #label>
                        <span>
                            {{ $t('form') }}
                            <el-badge type="primary" value="Alpha" />
                        </span>
                    </template>
                    <task-editor
                        ref="editor"
                        v-model="taskYaml"
                        :section="section"
                    />
                </el-tab-pane>
            </el-tabs>
        </el-drawer>
    </component>
</template>

<script setup>
    import CodeTags from "vue-material-design-icons/CodeTags.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import YamlUtils from "../../utils/yamlUtils";
    import Editor from "../inputs/Editor.vue";
    import TaskEditor from "./TaskEditor.vue";
    import {canSaveFlowTemplate, saveFlowTemplate} from "../../utils/flowTemplate";
    import {mapState} from "vuex";
    import Utils from "../../utils/utils";

    export default {
        components: {Editor, TaskEditor},
        props: {
            component: {
                type: String,
                default: "el-button"
            },
            task: {
                type: Object,
                default: undefined
            },
            taskId: {
                type: String,
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
            section: {
                type: String,
                default: "tasks"
            }
        },
        methods: {
            load(taskId) {
                if(this.revision){
                    return YamlUtils.extractTask(this.revisions[this.revision-1].source, taskId, this.section).toString();
                }
                return YamlUtils.extractTask(this.flow.source, taskId, this.section).toString();
            },
            mapSectionWithSchema() {
                switch (this.section) {
                    case "tasks":
                        return "task";
                    case "triggers":
                        return "trigger";
                    default:
                        return "task";
                }
            },
            saveTask() {
                let updatedSource;
                try {
                    updatedSource = YamlUtils.replaceTaskInDocument(
                        this.flow.source,
                        this.taskId ? this.taskId : this.task.id,
                        this.taskYaml,
                        this.section
                    );
                } catch (err) {
                    this.$toast().warning(
                        err.message,
                        this.$t("invalid yaml"),
                    );

                    return;
                }
                saveFlowTemplate(this, updatedSource, "flow")
                    .then(() => {
                        this.isModalOpen = false;
                    })
            },
            onShow() {
                this.isModalOpen = !this.isModalOpen;
                if (this.taskId || this.task.id) {
                    this.taskYaml = this.load(this.taskId ? this.taskId : this.task.id);
                } else {
                    this.taskYaml = YamlUtils.stringify(this.task);
                }
            },
        },
        data() {
            return {
                uuid: Utils.uid(),
                taskYaml: undefined,
                isModalOpen: false,
                activeTabs: "form",
            };
        },
        created() {

        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["revisions"]),
            canSave() {
                return canSaveFlowTemplate(true, this.user, {namespace: this.namespace}, "flow");
            },
            isLoading() {
                return this.taskYaml === undefined;
            },
            isReadOnly() {
                return this.flow && this.revision && this.flow.revision !== this.revision
            }
        }
    };
</script>
