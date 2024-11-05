<template>
    <div class="button-top">
        <el-button
            :icon="ContentSave"
            @click="$emit('save', source)"
            :type="buttonType"
            :disabled="source === initialSource"
        >
            {{ $t("save") }}
        </el-button>
    </div>
    <el-row>
        <el-col :span="$route.params?.id ? 12 : 24">
            <editor
                @save="$emit('save', $event)"
                v-model="source"
                schema-type="dashboard"
                lang="yaml"
                @update:model-value="source = $event"
                @cursor="updatePluginDocumentation"
                :creating="true"
                :read-only="false"
                :navbar="false"
            />
        </el-col>
        <el-col :span="12" v-if="$route.params?.id">
            <iframe class="w-100 h-100" :src="`http://localhost:5173/ui/dashboards/edit/${$route.params.id}`" />
        </el-col>
    </el-row>
</template>

<script>
    import Editor from "../../inputs/Editor.vue";
    import YamlUtils from "../../../utils/yamlUtils.js";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";

    export default {
        computed: {
            ContentSave() {
                return ContentSave
            },
            YamlUtils() {
                return YamlUtils
            },
            buttonType() {
                if (this.errors) {
                    return "danger";
                }

                return this.warnings
                    ? "warning"
                    : "primary";
            }
        },
        emits: ["save"],
        props: {
            initialSource: {
                type: String,
                default: undefined
            }
        },
        components: {
            Editor
        },
        methods: {
            updatePluginDocumentation(event) {
                const taskType = YamlUtils.getTaskType(
                    event.model.getValue(),
                    event.position
                );
                const pluginSingleList = this.$store.getters["plugin/getPluginSingleList"];
                if (taskType && pluginSingleList && pluginSingleList.includes(taskType)) {
                    this.$store.dispatch("plugin/load", {cls: taskType}).then((plugin) => {
                        this.$store.commit("plugin/setEditorPlugin", plugin);
                    });
                } else {
                    this.$store.commit("plugin/setEditorPlugin", undefined);
                }
            }
        },
        data() {
            return {
                source: this.initialSource,
                errors: undefined,
                warnings: undefined
            }
        },
        beforeUnmount() {
            this.$store.commit("plugin/setEditorPlugin", undefined);
        }
    };
</script>
