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
<script lang="ts" setup>
    import {onBeforeMount, onBeforeUnmount, ref, watch} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import TaskRoot from "./tasks/TaskRoot.vue";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import {useStore} from "vuex";
    import {SECTIONS} from "../../utils/constants";
    import {NoCodeElement, Schemas} from "../code/utils/types";

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const emit = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {
            type: String,
            required: false,
            default: undefined,
        },
        section: {
            type: String,
            required: true,
            default: undefined,
        },
    });

    const store = useStore();

    onBeforeMount(() => {
        if (props.modelValue) {
            setup()
        }
    })

    onBeforeUnmount(() => {
        store.commit("flow/setTaskError", undefined);
    })

    watch(() => props.modelValue, (v) => {
        if (!v) {
            taskObject.value = {};
            selectedTaskType.value = undefined;
        }
    })

    type PartialCodeElement = Partial<NoCodeElement>;

    const taskObject = ref<PartialCodeElement | undefined>({});
    const selectedTaskType = ref<string>();
    const isLoading = ref(false);
    const plugin = ref<{schema: Schemas}>();

    function setup() {
        taskObject.value = YAML_UTILS.parse<PartialCodeElement>(props.modelValue);
        selectedTaskType.value = taskObject.value?.type;
        store.dispatch("flow/validateTask", {task: props.modelValue, section: props.section})

        load();
    }

    function load() {
        isLoading.value = true;
        store
            .dispatch("plugin/load", {
                cls: selectedTaskType.value,
                all: true
            })
            .then((response) => {
                plugin.value = response;
                isLoading.value = false;
            })

    }

    function onInput(val: PartialCodeElement | undefined) {
        taskObject.value = val;
        emit("update:modelValue", YAML_UTILS.stringify(val));
    }

    function onTaskTypeSelect() {
        load();
        const value: PartialCodeElement = {
            type: selectedTaskType.value ?? ""
        };

        if (props.section !== SECTIONS.TRIGGERS && props.section !== SECTIONS.TASK_RUNNERS) {
            value["id"] = taskObject.value && taskObject.value.id ? taskObject.value.id : undefined;
        }

        onInput(value);
    }
</script>
<style lang="scss" scoped>
    .type-div {
        display: flex;
        justify-content: space-between;
    }
</style>
