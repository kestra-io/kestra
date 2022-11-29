<template>
    <component
        :is="component"
        :icon="icon.CodeTags"
        @click="onShow"
    >
        <span v-if="component !== 'el-button'">{{ $t('show task source') }}</span>
        <el-drawer
            :title="`Task ${taskId || task.id}`"
            v-if="isModalOpen"
            v-model="isModalOpen"
            destroy-on-close
            lock-scroll
            :append-to-body="true"
        >
            <template #footer>
                <el-button @click="saveTask" v-if="canSave" type="primary">
                    <content-save />&nbsp;
                    <span>{{ $t('save') }}</span>
                </el-button>
            </template>

            <editor
                ref="editor"
                v-if="taskYaml"
                @save="saveTask"
                v-model="taskYaml"
                :full-height="false"
                :navbar="false"
                lang="yaml"
            />
        </el-drawer>
    </component>
</template>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import Editor from "../../../components/inputs/Editor";
    import ContentSave from "vue-material-design-icons/ContentSave";
    import CodeTags from "vue-material-design-icons/CodeTags";
    import {canSaveFlowTemplate} from "../../../utils/flowTemplate";
    import {mapState} from "vuex";
    import Utils from "../../../utils/utils";
    import {shallowRef} from "vue";

    export default {
        components: {
            Editor,
            ContentSave,
            CodeTags,
        },
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
            load() {
                return this.$store.dispatch("flow/loadTask", {namespace: this.namespace, id: this.flowId, taskId: this.taskId});
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
                        this.isModalOpen = false;
                    })
            },
            onShow() {
                this.isModalOpen = !this.isModalOpen;
                if (this.taskId) {
                    this.load()
                        .then(value => {
                            this.taskYaml = YamlUtils.stringify(value);
                        })
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
                icon: {CodeTags: shallowRef(CodeTags)}
            };
        },
        created() {

        },
        computed: {
            ...mapState("auth", ["user"]),
            canSave() {
                return canSaveFlowTemplate(true, this.user, {namespace:this.namespace}, "flow");
            }
        }
    };
</script>
