<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <code>{{ $t("type") }}</code>
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
    import {SECTIONS} from "../../utils/constants.js";

    export default {
        inheritAttrs: false,
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
                this.setup()
            }
        },
        watch: {
            modelValue: {
                handler() {
                    if (!this.modelValue) {
                        this.taskObject = {};
                        this.selectedTaskType = undefined;
                    }
                }
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
            setup() {
                this.taskObject = YamlUtils.parse(this.modelValue);
                this.selectedTaskType = this.taskObject.type;
                this.$store.dispatch("flow/validateTask", {task: this.modelValue, section: this.section})

                this.load();
            },
            load() {
                this.isLoading = true;
                this.$store
                    .dispatch("plugin/load", {
                        cls: this.selectedTaskType,
                        all: true
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

                if (this.section !== SECTIONS.TRIGGERS && this.section !== SECTIONS.TASK_RUNNERS) {
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
