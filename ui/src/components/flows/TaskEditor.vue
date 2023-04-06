<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <code>type</code>
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
    import {mapGetters} from "vuex";

    export default {
        computed: {
            ...mapGetters("flow", ["taskError"]),
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
                taskObject: {},
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
                this.$emit("update:modelValue", YamlUtils.stringify(value));
            },
            onTaskTypeSelect() {
                this.load();
                this.taskObject.type = this.selectedTaskType;
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
<style lang="scss" scoped>
    .type-div {
        display: flex;
        justify-content: space-between;
    }
</style>
