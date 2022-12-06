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

<script setup>
    import CodeTags from "vue-material-design-icons/CodeTags.vue";
    import ContentSave from "vue-material-design-icons/ContentSave";
</script>

<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import Editor from "../../../components/inputs/Editor";
    import {canSaveFlowTemplate} from "../../../utils/flowTemplate";
    import {mapState} from "vuex";
    import Utils from "../../../utils/utils";

    export default {
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
