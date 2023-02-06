<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="typeDiv">
                    <code>{{ $t("type") }}</code>
                    <el-tooltip :disabled="!taskError" :content="taskErrorContent" raw-content>
                        <el-button :type="taskError ? 'danger' : 'success'" :icon="taskError ? Close : Check" />
                    </el-tooltip>
                </div>
            </template>
            <plugin-select
                v-model="selectedTaskType"
                :section="section"
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>

    <task-root
        v-loading="isLoading"
        v-if="plugin"
        name="root"
        :model-value="taskObject"
        @update:model-value="onInput"
        :schema="plugin.schema"
        :definitions="plugin.schema.definitions"
    />
</template>
<script>
    import TaskRoot from "./tasks/TaskRoot.vue";
    import YamlUtils from "../../utils/yamlUtils";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import {mapGetters} from "vuex";

    export default {
        computed: {
            ...mapGetters("flow", ["taskError"]),
            Check() {
                return Check
            },
            Close() {
                return Close
            },
            taskErrorContent() {
                return this.taskError
                    ? "<pre style='max-width: 40vw; white-space: pre-wrap'>" + this.taskError + "</pre>"
                    :  ""
            }
        },
        components: {
            TaskRoot,
            PluginSelect
        },
        emits: ["update:modelValue"],
        created() {
            if (this.modelValue) {
                this.taskObject = YamlUtils.parse(this.modelValue);
                this.selectedTaskType = this.taskObject.type;
                this.$store.dispatch("flow/validateTask", {task: this.modelValue})

                this.load();
            }
        },
        beforeUnmount() {
            this.$store.commit("flow/setTaskError", undefined);
        },
        props: {
            modelValue: {
                type: String,
                required: false,
                default: undefined,
            },
            section: {
                type: String,
                required: true,
                default: undefined,
            }
        },
        data() {
            return {
                selectedTaskType: undefined,
                taskObject: undefined,
                isLoading: false,
                plugin: undefined,
            };
        },
        methods: {
            load() {
                this.isLoading = true;
                this.$store
                    .dispatch("plugin/load", {
                        cls: this.selectedTaskType,
                    })
                    .then((response) => {
                        this.plugin = response;
                        this.isLoading = false;
                    })

            },
            onInput(value) {
                clearTimeout(this.timer);
                this.timer = setTimeout(() => {
                    this.taskObject = value;
                    this.$store.dispatch("flow/validateTask", {task: value})
                    this.$emit("update:modelValue", YamlUtils.stringify(value));
                }, 500);
            },
            onTaskTypeSelect() {
                this.load();

                const value = {
                    type: this.selectedTaskType
                };

                if (this.section !== "conditions") {
                    value["id"] = this.taskObject && this.taskObject.id ? this.taskObject.id : "";
                }

                this.onInput(value);
            },
        },
    };
</script>
<style lang="scss">
    .typeDiv {
        display: flex;
        justify-content: space-between;
    }
</style>
