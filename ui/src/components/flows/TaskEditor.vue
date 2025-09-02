<template>
    <div v-if="playgroundStore.enabled && isTask && taskObject?.id" class="flow-playground">
        <PlaygroundRunTaskButton :task-id="taskObject?.id" />
    </div>
    <el-form v-if="isTaskDefinitionBasedOnType" label-position="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <span class="asterisk">*</span>
                    <code>{{ t("type") }}</code>
                </div>
            </template>
            <PluginSelect
                v-model="selectedTaskType"
                :block-schema-path
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>
    <div @click="isPlugin && pluginsStore.updateDocumentation(taskObject as Parameters<typeof pluginsStore.updateDocumentation>[0])">
        <TaskObject
            v-loading="isLoading"
            v-if="(selectedTaskType || !isTaskDefinitionBasedOnType) && schemaProp"
            name="root"
            :model-value="taskObject"
            @update:model-value="onTaskInput"
            :schema="schemaProp"
            :properties="properties"
            :definitions="fullSchema.definitions"
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed, inject, onActivated, provide, ref, toRaw, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TaskObject from "../code/components/tasks/TaskObject.vue";
    import PluginSelect from "../../components/plugins/PluginSelect.vue";
    import {NoCodeElement, Schemas} from "../code/utils/types";
    import {
        FIELDNAME_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
    } from "../code/injectionKeys";
    import {removeNullAndUndefined} from "../code/utils/cleanUp";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {usePlaygroundStore} from "../../stores/playground";
    import {getValueAtJsonPath, resolve$ref} from "../../utils/utils";
    import PlaygroundRunTaskButton from "../inputs/PlaygroundRunTaskButton.vue";

    const {t} = useI18n();

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const modelValue = defineModel<string>();

    const pluginsStore = usePluginsStore();
    const playgroundStore = usePlaygroundStore();

    type PartialCodeElement = Partial<NoCodeElement>;

    const taskObject = ref<PartialCodeElement | undefined>({});
    const selectedTaskType = ref<string>();
    const isLoading = ref(false);
    const plugin = ref<{schema: Schemas}>();

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const fieldName = inject(FIELDNAME_INJECTION_KEY, undefined);


    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""));

    const isTask = computed(() => ["task", "tasks"].includes(parentPath.split(".").pop() ?? ""));

    const isPluginDefaults = computed(() => {
        return parentPath.startsWith("pluginDefaults")
    });

    const isPlugin = computed(() => {
        return parentPath !== "inputs"
    });

    const schemaAtBlockPath = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value))
    const isTaskDefinitionBasedOnType = computed(() => {
        if(isPluginDefaults.value){
            return true
        }
        const firstAnyOf = Array.isArray(schemaAtBlockPath.value?.anyOf) ? schemaAtBlockPath.value?.anyOf[0] : undefined;
        if (!firstAnyOf) return false;
        if(firstAnyOf.properties){
            return firstAnyOf?.properties?.type !== undefined;
        }
        if(Array.isArray(firstAnyOf.allOf)){
            return firstAnyOf.allOf.some((item: any) => {
                return resolve$ref(fullSchema.value, item)
                    .properties?.type !== undefined;
            });
        }
        return true
    });

    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => selectedTaskType.value ? `#/definitions/${resolvedType.value}` : blockSchemaPath.value));

    watch(modelValue, (v) => {
        if (!v) {
            taskObject.value = {};
            selectedTaskType.value = undefined;
        } else {
            setup()
        }
    }, {immediate: true});

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<{
        definitions: Record<string, any>,
        $ref: string,
    }>({
        definitions: {},
        $ref: "",
    }));

    const schema = computed(() => plugin.value?.schema);

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
        const prop = isTaskDefinitionBasedOnType.value
            ? schema.value?.properties
            : schemaAtBlockPath.value

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
            pluginsStore.updateDocumentation(taskObject.value as Parameters<typeof pluginsStore.updateDocumentation>[0]);
        }
    });

    const fieldDefinition = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value));

    // useful to map inputs to their real schema
    const typeMap = computed<Record<string, string>>(() => {
        if (fieldDefinition.value?.anyOf) {
            const f = fieldDefinition.value.anyOf.reduce((acc: Record<string, string>, item: any) => {
                if (item.$ref) {
                    const i = getValueAtJsonPath(fullSchema.value, item.$ref);
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

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}));
    const resolvedType = computed(() => typeMap.value[selectedTaskType.value ?? ""] ?? selectedTaskType.value ?? "");

    function load() {
        // try to resolve the type from local schema
        if (definitions.value?.[resolvedType.value]) {
            const defs = definitions.value ?? {}
            plugin.value = {
                schema: {
                    properties: defs[resolvedType.value],
                    definitions: defs,
                }
            };
            return;
        }
    }

    watch([selectedTaskType, fullSchema], ([task]) => {
        if (task) {
            load();
            if(isPlugin.value){
                pluginsStore.updateDocumentation(taskObject.value as Parameters<typeof pluginsStore.updateDocumentation>[0]);
            }
        }
    }, {immediate: true});



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

    .flow-playground{
        display: flex;
        justify-content: end;
    }
</style>
