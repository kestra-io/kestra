<template>
    <b-modal
        :id="modalId"
        :title="`Task ${task.id}`"
        hide-backdrop
        modal-class="right"
        size="xl"
        @shown="onShow"
    >
        <template #modal-footer>
            <b-button @click="saveTask" v-if="canSave">
                <content-save />&nbsp;
                <span>{{ $t('save') }}</span>
            </b-button>
        </template>

        <editor
            ref="editor"
            @onSave="saveTask"
            v-model="taskYaml"
            theme="vs"
            :full-height="false"
            :navbar="false"
            lang="yaml"
        />
    </b-modal>
</template>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import Editor from "../../../components/inputs/Editor";
    import ContentSave from "vue-material-design-icons/ContentSave";
    import {canSaveFlowTemplate} from "../../../utils/flowTemplate";
    import {mapState} from "vuex";

    export default {
        components: {
            Editor,
            ContentSave,
        },
        props: {
            task: {
                type: Object,
                required: true
            },
            flowId: {
                type: String,
                required: true
            },
            namespace: {
                type: String,
                required: true
            },
            modalId: {
                type: String,
                required: true
            },
        },
        methods: {
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
                if (this.$refs.editor) {
                    this.$refs.editor.onResize();
                }
            }
        },
        data() {
            return {
                taskYaml: undefined,
            };
        },
        created() {
            this.taskYaml = YamlUtils.stringify(this.task);
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
