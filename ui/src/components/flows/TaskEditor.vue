<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <span class="asterisk">*</span>
                    <code>{{ $t("type") }}</code>
                </div>
            </template>
            <PluginSelect
                v-if="blockType"
                v-model="selectedTaskType"
                :block-type="blockType"
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>

    <TaskObject
        v-loading="isLoading"
        v-if="selectedTaskType && schema"
        name="root"
        :model-value="taskObject"
        @update:model-value="onTaskInput"
        :schema="schemaProp"
        :properties="properties"
        :definitions="schema.definitions"
    />
</template>

<script lang="ts" setup>
    import {computed, inject, onActivated, ref, toRaw, watch} from "vue";
    import {useStore} from "vuex";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TaskObject from "./tasks/TaskObject.vue";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import {NoCodeElement, Schemas} from "../code/utils/types";
    import {BLOCKTYPE_INJECT_KEY, PARENT_PATH_INJECTION_KEY} from "../code/injectionKeys";

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const modelValue = defineModel<string>();

    const store = useStore();

    type PartialCodeElement = Partial<NoCodeElement>;

    const taskObject = ref<PartialCodeElement | undefined>({});
    const selectedTaskType = ref<string>();
    const isLoading = ref(false);
    const plugin = ref<{schema: Schemas}>();

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const blockType = inject(BLOCKTYPE_INJECT_KEY, "");

    const isPluginDefaults = computed(() => {
        return parentPath.startsWith("pluginDefaults")
    });

    watch(modelValue, (v) => {
        if (!v) {
            taskObject.value = {};
            selectedTaskType.value = undefined;
        } else {
            setup()
        }
    }, {immediate: true});

    const schema = computed(() => {
        return plugin.value?.schema;
    });

    const properties = computed(() => {
        const updatedProperties = schemaProp.value?.properties;
        if(isPluginDefaults.value){
            updatedProperties["id"] = undefined
            updatedProperties["forced"] = {type: "boolean", $required: true};

            return updatedProperties;
        }
        if(!updatedProperties?.id && ["triggers", "tasks"].includes(blockType ?? "")){
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


    }

    // when tab is clicked, load the documentation
    onActivated(() => {
        if(selectedTaskType.value){
            store.dispatch("plugin/updateDocumentation", {task: selectedTaskType.value});
        }
    });

    watch(selectedTaskType, (task) => {
        if (task) {
            load();
            store.dispatch("plugin/updateDocumentation", {task});
        }
    }, {immediate: true});

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

    function onTaskInput(val: PartialCodeElement | undefined) {
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
        modelValue.value = YAML_UTILS.stringify(toRaw(val));
    }

    function onTaskTypeSelect() {
        load();
        const value: PartialCodeElement = {
            type: selectedTaskType.value ?? ""
        };

        onTaskInput(value);
    }
</script>
<style lang="scss" scoped>
    .type-div {
        display: flex;
        justify-content: space-between;
        text-transform: lowercase;
        align-items: center;
        gap: 0.25rem;
        font-weight: 600;
        .asterisk {
            color: var(--ks-content-alert);
        }
        code {
            color: var(--ks-content-primary);
        }
    }
</style>
