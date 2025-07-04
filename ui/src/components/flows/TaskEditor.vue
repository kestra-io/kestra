<template>
    <el-form label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <span class="asterisk">*</span>
                    <code>{{ t("type") }}</code>
                </div>
            </template>
            <PluginSelect
                v-model="selectedTaskType"
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>

    <div @click="if(isPlugin)pluginsStore.updateDocumentation({task: selectedTaskType});">
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
    </div>
</template>

<script lang="ts" setup>
    import {computed, inject, onActivated, provide, ref, toRaw, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TaskObject from "./tasks/TaskObject.vue";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import {NoCodeElement, Schemas} from "../code/utils/types";
    import {
        SCHEMA_PATH_INJECTION_KEY,
        FIELDNAME_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY
    } from "../code/injectionKeys";
    import {removeNullAndUndefined} from "../code/utils/cleanUp";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {getValueAtJsonPath} from "../../utils/utils";

    const {t} = useI18n();

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const modelValue = defineModel<string>();

    const pluginsStore = usePluginsStore();

    type PartialCodeElement = Partial<NoCodeElement>;

    const taskObject = ref<PartialCodeElement | undefined>({});
    const selectedTaskType = ref<string>();
    const isLoading = ref(false);
    const plugin = ref<{schema: Schemas}>();

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const fieldName = inject(FIELDNAME_INJECTION_KEY, undefined);

    provide(SCHEMA_PATH_INJECTION_KEY, computed(() => `#/definitions/${selectedTaskType.value}`))

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, "");

    const isPluginDefaults = computed(() => {
        return parentPath.startsWith("pluginDefaults")
    });

    const isPlugin = computed(() => {
        return parentPath !== "inputs"
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
            updatedProperties["forced"] = {
                type: "boolean",
                $required: true
            };

            return updatedProperties;
        }

        if(!updatedProperties?.id && (parentPath.endsWith("task")
            || parentPath.endsWith("tasks")
            || parentPath.endsWith("triggers"))){
            updatedProperties["id"] = {
                type: "string",
                $required: true
            };
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

    // when tab is opened, load the documentation
    onActivated(() => {
        if(selectedTaskType.value && parentPath !== "inputs"){
            pluginsStore.updateDocumentation({task: selectedTaskType.value});
        }
    });

    // useful to map inputs to their real schema
    const typeMap = computed<Record<string, string>>(() => {
        const field = getValueAtJsonPath(pluginsStore.flowSchema, blockSchemaPath)

        if (field?.anyOf) {
            const f = field.anyOf.reduce((acc: Record<string, string>, item: any) => {
                if (item.$ref) {
                    const i = getValueAtJsonPath(pluginsStore.flowSchema, item.$ref);
                    if(i) item = i;
                }
                if (item.allOf) {
                    let type = "", ref;
                    for (const subItem of item.allOf) {
                        if (subItem.properties?.type?.const) {
                            type = subItem.properties.type.const;
                        }
                        if (subItem.$ref) {
                            ref = removeRefPrefix(subItem.$ref)
                        }
                    }
                    if (type && ref) {
                        acc[type] = ref;
                    }
                }
                return acc;
            }, {});

            return f;
        }

        return {}
    });

    watch([selectedTaskType, () => pluginsStore.flowSchema], ([task]) => {
        if (task) {
            load();
            if(isPlugin.value){
                pluginsStore.updateDocumentation({task});
            }
        }
    }, {immediate: true});

    function load() {
        const resolvedType = typeMap.value[selectedTaskType.value ?? ""] ?? selectedTaskType.value ?? "";
        // try to resolve the type from local schema
        if (pluginsStore.flowDefinitions?.[resolvedType]) {
            const defs = pluginsStore.flowDefinitions ?? {}
            plugin.value = {
                schema: {
                    properties: defs[resolvedType],
                    definitions: defs,
                }
            };
            return;
        }
    }

    function onTaskInput(val: PartialCodeElement | undefined) {
        taskObject.value = val;
        if(fieldName){
            val = {
                [fieldName]: val,
            };
        }
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
        modelValue.value = YAML_UTILS.stringify(removeNullAndUndefined(toRaw(val)));
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
