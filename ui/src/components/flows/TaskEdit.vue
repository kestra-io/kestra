<template>
    <component
        :is="component"
        :icon="CodeTags"
        @click="onShow"
    >
        <span v-if="component !== 'el-button'">{{ $t('show task source') }}</span>
        <el-drawer
            :title="`Task ${taskId || task.id}`"
            v-if="isModalOpen"
            v-model="isModalOpen"
            destroy-on-close
            lock-scroll
            size=""
            :append-to-body="true"
        >
            <template #footer>
                <el-button :icon="ContentSave" @click="saveTask" v-if="canSave" type="primary">
                    {{ $t('save') }}
                </el-button>
            </template>

            <editor
                ref="editor"
                @save="saveTask"
                v-model="taskYaml"
                schema-type="task"
                :full-height="false"
                :navbar="false"
                lang="yaml"
            />
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
    import {canSaveFlowTemplate, saveFlowTemplate} from "../../utils/flowTemplate";
    import {mapState} from "vuex";
    import Utils from "../../utils/utils";

    export default {
        components: {Editor},
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
        },
        methods: {
            load(taskId) {
                return YamlUtils.extractTask(this.flow.source, taskId).toString();
            },
            saveTask() {
                let updatedSource;
                try {
                    updatedSource = YamlUtils.replaceTaskInDocument(
                        this.flow.source,
                        this.taskId ? this.taskId : this.task.id,
                        this.taskYaml
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
                    this.taskYaml = this.load(this.taskId ? this.taskId : this.task.id)
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
            };
        },
        created() {

        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            canSave() {
                return canSaveFlowTemplate(true, this.user, {namespace: this.namespace}, "flow");
            }
        }
    };
</script>
