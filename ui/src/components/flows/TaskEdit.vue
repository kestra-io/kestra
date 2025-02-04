<template>
    <component
        :is="component"
        :icon="CodeTags"
        @click="onShow"
        ref="taskEdit"
    >
        <span v-if="component !== 'el-button' && !isHidden">{{ $t("show task source") }}</span>
        <drawer
            v-if="isModalOpen"
            v-model="isModalOpen"
        >
            <template #header>
                <code>{{ taskId || task?.id || $t("add task") }}</code>
            </template>
            <template #footer>
                <div v-loading="isLoading">
                    <ValidationError class="me-2" link :errors="errors" />

                    <el-button
                        :icon="ContentSave"
                        @click="saveTask"
                        v-if="canSave && !readOnly"
                        :disabled="errors && !!errors.length"
                        type="primary"
                    >
                        {{ $t("save task") }}
                    </el-button>
                    <el-alert
                        show-icon
                        :closable="false"
                        class="mb-0 mt-3"
                        v-if="revision && revisions?.length !== revision"
                        type="warning"
                    >
                        <strong>{{ $t("seeing old revision", {revision: revision}) }}</strong>
                    </el-alert>
                </div>
            </template>

            <el-tabs v-model="activeTabs">
                <el-tab-pane v-if="!readOnly" name="form">
                    <template #label>
                        <span>{{ $t("form") }}</span>
                    </template>
                    <task-editor
                        ref="editor"
                        v-model="taskYaml"
                        :section="section"
                        @update:model-value="onInput"
                    />
                </el-tab-pane>
                <el-tab-pane name="source">
                    <template #label>
                        <span>{{ $t("source") }}</span>
                    </template>
                    <editor
                        :read-only="readOnly"
                        ref="editor"
                        @save="saveTask"
                        v-model="taskYaml"
                        :schema-type="section.toLowerCase()"
                        :full-height="false"
                        :navbar="false"
                        lang="yaml"
                        @update:model-value="onInput"
                    />
                </el-tab-pane>
                <el-tab-pane v-if="pluginMardown" name="documentation">
                    <template #label>
                        <span>
                            {{ $t("documentation.documentation") }}
                        </span>
                    </template>
                    <div class="documentation">
                        <markdown :source="pluginMardown" />
                    </div>
                </el-tab-pane>
            </el-tabs>
        </drawer>
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
    import Drawer from "../Drawer.vue";
    import {canSaveFlowTemplate} from "../../utils/flowTemplate";
    import {mapGetters, mapState} from "vuex";
    import Utils from "../../utils/utils";
    import Markdown from "../layout/Markdown.vue";
    import ValidationError from "./ValidationError.vue";
    import {SECTIONS} from "../../utils/constants";

    export default {
        components: {Editor, TaskEditor, Drawer, Markdown, ValidationError},
        emits: ["update:task", "close"],
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
                default: SECTIONS.TASKS,
                validator(value) {
                    return [SECTIONS.TASKS, SECTIONS.TRIGGERS].includes(value)
                }
            },
            emitOnly: {
                type: Boolean,
                default: false
            },
            emitTaskOnly: {
                type: Boolean,
                default: false
            },
            isHidden: {
                type: Boolean,
                default: false
            },
            readOnly: {
                type: Boolean,
                default: false
            },
            flowSource: {
                type: String,
                default: undefined
            }
        },
        watch: {
            task: {
                async handler() {
                    if (this.task) {
                        this.taskYaml = YamlUtils.stringify(this.task);
                        if (this.task.type) {
                            this.$store.dispatch("plugin/load", {cls: this.task.type})
                        }
                    } else {
                        this.taskYaml = "";
                    }
                },
                immediate: true
            },
            taskYaml: {
                handler() {
                    const task = YamlUtils.parse(this.taskYaml);
                    if (task?.type && task.type !== this.type) {
                        this.$store.dispatch("plugin/load", {cls: task.type})
                        this.type = task.type
                    }
                },
            },
            isModalOpen: {
                handler() {
                    if (!this.isModalOpen) {
                        this.$emit("close");
                        this.activeTabs = this.defaultActiveTab();
                    }
                }
            }
        },
        methods: {
            async load(taskId) {
                if (this.revision) {
                    if (this.revisions?.[this.revision - 1] === undefined) {
                        this.revisions = await this.$store
                            .dispatch("flow/loadRevisions", {
                                namespace: this.namespace,
                                id: this.flowId,
                                store: false
                            });
                    }
                }

                return YamlUtils.extractTask(this.source, taskId).toString();
            },
            saveTask() {
                this.$emit("update:task", this.taskYaml);
                this.taskYaml = "";
                this.isModalOpen = false;
            },
            async onShow() {
                this.isModalOpen = !this.isModalOpen;
                if (this.taskId) {
                    this.taskYaml = await this.load(this.taskId ? this.taskId : this.task.id);
                } else if (this.task) {
                    this.taskYaml = YamlUtils.stringify(this.task);
                }
                if (this.task?.type) {
                    this.$store
                        .dispatch("plugin/load", {cls: this.task.type})
                }
            },
            onInput(value) {
                clearTimeout(this.timer);
                this.timer = setTimeout(() => {
                    this.$store.dispatch("flow/validateTask", {task: value, section: this.section})
                }, 500);
            },
            defaultActiveTab() {
                return this.readOnly ? "source" : "form";
            }
        },
        data() {
            return {
                uuid: Utils.uid(),
                taskYaml: "",
                isModalOpen: false,
                activeTabs: this.defaultActiveTab(),
                type: null,
                revisions: undefined
            };
        },
        computed: {
            ...mapGetters("flow", ["taskError"]),
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["plugin"]),
            errors() {
                return this.taskError?.split(/, ?/)
            },
            pluginMardown() {
                if (this.plugin && this.plugin.markdown && YamlUtils.parse(this.taskYaml)?.type) {
                    return this.plugin.markdown
                }
                return null
            },
            canSave() {
                return canSaveFlowTemplate(true, this.user, {namespace: this.namespace}, "flow");
            },
            isLoading() {
                return this.taskYaml === undefined;
            },
            source() {
                return this.revision ? this.revisions?.[this.revision - 1]?.source : this.flow?.source;
            }
        }
    };
</script>
<style scoped lang="scss">
    // Required, otherwise the doc titles and properties names are not visible
    .documentation {
        padding: 1rem;
    }
</style>
