<template>
    <component
        :is="component"
        v-b-modal="`modal-source-${uuid}`"
    >
        <kicon :tooltip="$t('show task source')">
            <code-tags />
        </kicon>

        <span v-if="component !== 'b-button'">{{ $t('show task source') }}</span>

        <b-modal
            :id="`modal-source-${uuid}`"
            :title="`Task ${taskId || task.id}`"
            hide-backdrop
            modal-class="right"
            size="xl"
            @show="onShow"
            @shown="onShown"
        >
            <template #modal-footer>
                <b-button @click="saveTask" v-if="canSave" variant="primary">
                    <content-save />&nbsp;
                    <span>{{ $t('save') }}</span>
                </b-button>
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
        </b-modal>
    </component>
</template>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import Editor from "../../../components/inputs/Editor";
    import ContentSave from "vue-material-design-icons/ContentSave";
    import Kicon from "../../../components/Kicon"
    import CodeTags from "vue-material-design-icons/CodeTags";
    import {canSaveFlowTemplate} from "../../../utils/flowTemplate";
    import {mapState} from "vuex";
    import Utils from "../../../utils/utils";

    export default {
        components: {
            Editor,
            ContentSave,
            Kicon,
            CodeTags,
        },
        props: {
            component: {
                type: String,
                default: "b-button"
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
                    })
            },
            onShow() {
                if (this.taskId) {
                    this.load()
                        .then(value => {
                            this.taskYaml = YamlUtils.stringify(value);
                        })
                } else {
                    this.taskYaml = YamlUtils.stringify(this.task);
                }
            },
            onShown() {
                if (this.$refs.editor) {
                    this.$refs.editor.onResize();
                }
            }
        },
        data() {
            return {
                uuid: Utils.uid(),
                taskYaml: undefined,
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
<style scoped lang="scss">

</style>
