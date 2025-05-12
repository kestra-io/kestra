<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <code>{{ $t("type") }}</code>
                </div>
            </template>
            <PluginSelect
                v-model="selectedTaskType"
                :section="section"
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>

    <TaskObject
        v-loading="isLoading"
        v-if="schema"
        name="root"
        :model-value="taskObject"
        @update:model-value="onInput"
        :schema="schemaProp"
        :properties="properties"
        :definitions="schema.definitions"
    />
</template>

<script lang="ts" setup>
    import {computed, onBeforeMount, ref, watch} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";
    import TaskObject from "./tasks/TaskObject.vue";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import {useStore} from "vuex";
    import {PLUGIN_DEFAULTS_SECTION, SECTIONS} from "../../utils/constants";
    import {NoCodeElement, Schemas, SectionKey} from "../code/utils/types";

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const modelValue = defineModel<string>();

    const props = defineProps<{
        section: SectionKey
    }>();

    const store = useStore();

    onBeforeMount(() => {
        if (modelValue.value) {
            setup()
        }
    })

    watch(modelValue, (v) => {
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

    const schema = computed(() => {
        return plugin.value?.schema;
    });

    const isPluginDefaults = computed(() => {
        return props.section === PLUGIN_DEFAULTS_SECTION
    });

    const properties = computed(() => {
        const updatedProperties = schemaProp.value?.properties;
        if(isPluginDefaults.value){
            updatedProperties["id"] = undefined
            updatedProperties["forced"] = {type: "boolean", $required: true};

            return updatedProperties;
        }
        if(!updatedProperties?.id){
            updatedProperties["id"] = {type: "string", $required: true};
        }
        return updatedProperties
    });

    const schemaProp = computed(() => {
        const prop = schema.value?.properties;
        if(!prop){
            return undefined;
        }
        prop.required = prop.required || [];
        prop.required.push("id");
        if(isPluginDefaults.value){
            prop.required.push("forced");
        }
        return prop;
    });

    function setup() {
        const parsed = YAML_UTILS.parse<PartialCodeElement>(modelValue.value);
        if(isPluginDefaults.value){
            const {forced, type, values} = parsed as any;
            taskObject.value = {...values, forced, type};
        }else{
            taskObject.value = parsed;
        }
        selectedTaskType.value = taskObject.value?.type;

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
        if (isPluginDefaults.value) {
            const {
                forced,
                type,
                id: _,
                ...rest
            } = val as any;

            if(Object.keys(rest).length){
                val = {
                    type,
                    forced,
                    values: rest,
                };
            }
        }
        modelValue.value = YAML_UTILS.stringify(val);
    }

    function onTaskTypeSelect() {
        load();
        const value: PartialCodeElement = {
            type: selectedTaskType.value ?? ""
        };

        if (props.section !== SECTIONS.TRIGGERS && props.section !== SECTIONS.TASK_RUNNERS) {
            value["id"] = taskObject.value?.id ? taskObject.value.id : undefined;
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
