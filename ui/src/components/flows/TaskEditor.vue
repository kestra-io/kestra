<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <code>{{ $t('type') }}</code>&nbsp;
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

    export default {
        components: {
            TaskRoot,
            PluginSelect
        },
        emits: ["update:modelValue"],
        created() {
            if (this.modelValue) {
                this.taskObject = YamlUtils.parse(this.modelValue);
                this.selectedTaskType = this.taskObject.type;

                this.load();
            }
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
                plugin: undefined
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
                this.taskObject = value;
                this.$emit("update:modelValue", YamlUtils.stringify(value));
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

