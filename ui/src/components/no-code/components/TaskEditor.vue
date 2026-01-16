<template>
    <div v-if="playgroundStore.enabled && isTask && taskModel?.id" class="flow-playground">
        <PlaygroundRunTaskButton :taskId="taskModel?.id" />
    </div>
    <el-form v-if="isTaskDefinitionBasedOnType" labelPosition="top">
        <el-form-item>
            <template #label>
                <div class="type-div">
                    <span class="asterisk">*</span>
                    <code>{{ $t("type") }}</code>
                </div>
            </template>
            <PluginSelect
                v-model="selectedTaskType"
                :blockSchemaPath
                @update:model-value="onTaskTypeSelect"
            />
        </el-form-item>
    </el-form>
    <div @click="() => onTaskEditorClick(taskModel)">
        <TaskObject
            v-loading="isLoading || isPluginSchemaLoading"
            v-if="(selectedTaskType || !isTaskDefinitionBasedOnType) && schema"
            name="root"
            :modelValue="taskModel"
            @update:model-value="onTaskInput"
            :schema
            :properties
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, onActivated, provide, ref, toRaw, watch} from "vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import TaskObject from "./tasks/TaskObject.vue";
    import PluginSelect from "../../plugins/PluginSelect.vue";
    import {NoCodeElement, Schemas} from "../utils/types";
    import {
        FIELDNAME_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
        DATA_TYPES_MAP_INJECTION_KEY,
        ON_TASK_EDITOR_CLICK_INJECTION_KEY,
    } from "../injectionKeys";
    import {removeNullAndUndefined} from "../utils/cleanUp";
    import {removeRefPrefix, usePluginsStore} from "../../../stores/plugins";
    import {usePlaygroundStore} from "../../../stores/playground";
    import {getValueAtJsonPath, resolve$ref} from "../../../utils/utils";
    import PlaygroundRunTaskButton from "../../inputs/PlaygroundRunTaskButton.vue";
    import isEqual from "lodash/isEqual";
    import {useMiscStore} from "../../../override/stores/misc";

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    });

    const modelValue = defineModel<string>();

    const pluginsStore = usePluginsStore();
    const playgroundStore = usePlaygroundStore();

    type PartialNoCodeElement = Partial<NoCodeElement>;

    const taskModel = ref<PartialNoCodeElement | undefined>({});
    const selectedTaskType = ref<string>();
    const isLoading = ref(false);

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
            taskModel.value = {};
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



    const properties = computed(() => {
        const updatedProperties = resolvedProperties.value ?? {};
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

    function setup() {
        const parsed = YAML_UTILS.parse<PartialNoCodeElement>(modelValue.value);
        if(isPluginDefaults.value){
            const {forced, type, values} = parsed as any;
            taskModel.value = {...values, forced, type};
        }else{
            taskModel.value = parsed;
        }
        selectedTaskType.value = taskModel.value?.type;
    }

    // when tab is opened, load the documentation
    onActivated(() => {
        if(selectedTaskType.value && parentPath !== "inputs"){
            pluginsStore.updateDocumentation({type: selectedTaskType.value, ...taskModel.value});
        }
    });

    const fieldDefinition = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value));

    // useful to map inputs to their real schema
    // NOTE: there can be more than one schema per type (ex: KPI chart could be for flow or for executions.)
    const typeMap = computed<Record<string, string[]>>(() => {
        if (fieldDefinition.value?.anyOf) {
            const f = fieldDefinition.value.anyOf.reduce((acc: Record<string, string[]>, item: any) => {
                if (item.$ref) {
                    const resolvedItem = getValueAtJsonPath(fullSchema.value, item.$ref);
                    if (resolvedItem?.allOf) {
                        let type = "", ref;
                        for (const subItem of resolvedItem.allOf) {
                            if (subItem.properties?.type?.const) {
                                type = subItem.properties.type.const;
                            }
                            if (subItem.$ref) {
                                ref = removeRefPrefix(subItem.$ref)
                            }
                        }
                        if (type && ref) {
                            acc[type] = acc[type] || [];
                            acc[type].push(ref);
                        }
                    }

                    const typeAsConst = resolvedItem?.properties?.type?.const

                    if (typeAsConst) {
                        acc[typeAsConst] = acc[typeAsConst] || [];
                        acc[typeAsConst].push(removeRefPrefix(item.$ref));
                    }
                }
                return acc;

            }, {});

            return f;
        }

        return {}
    });

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}));

    const resolvedTypes = computed<string[]>(() => {
        return typeMap.value[selectedTaskType.value ?? ""] || [];
    });

    const versionedSchema = ref<Schemas|undefined>()
    const isPluginSchemaLoading = ref(false)

    watch([selectedTaskType, resolvedTypes], async ([val, types]) => {
        if(types.length > 1 && val){
            isPluginSchemaLoading.value = true;
            try{
                const {schema} = await pluginsStore.load({
                    cls: val,
                    version: taskModel.value?.version,
                })
                versionedSchema.value = schema?.properties
            } finally {
                isPluginSchemaLoading.value = false;
            }
        }
    }, {immediate: true}); 

    const resolvedType = computed<string>(() => {
        if(resolvedTypes.value.length > 1 && selectedTaskType.value){
            // find the resolvedType that match the current dataType
            const dataType = taskModel.value?.data?.type;
            if(dataType){
                for(const typeLocal of resolvedTypes.value){
                    const schema = definitions.value?.[typeLocal];
                    const dataResolved = schema.properties?.data?.$ref
                        ? getValueAtJsonPath(fullSchema.value, schema.properties?.data.$ref)
                        : schema.properties?.data;
                    const typeConst = dataResolved?.properties?.type?.const
                    if(typeConst === dataType){
                        return typeLocal;
                    }
                }
            }
        }

        return resolvedTypes.value
            ? (resolvedTypes.value.length === 1
                ? resolvedTypes.value[0]
                : selectedTaskType.value ?? "")
            : "";
    });

    const resolvedSchemas = computed(() => {
        return resolvedTypes.value.map((type) => definitions.value?.[type]);
    });

    const REQUIRED_FIELDS = ["id", "data"];

    const schema = computed(() => {
        const localSchema = resolvedLocalSchema.value;
        if(isTaskDefinitionBasedOnType.value){
            localSchema.required = localSchema.required ?? [];
            for(const field of REQUIRED_FIELDS){
                if(!localSchema.required.includes(field) && resolvedProperties.value?.[field]){
                    localSchema.required.push(field);
                }
            }
        }
        return localSchema;
    });

    const resolvedLocalSchema = computed(() => {
        return versionedSchema.value ?? (isTaskDefinitionBasedOnType.value
            ? definitions.value?.[resolvedType.value] ?? {}
            : schemaAtBlockPath.value)
    });

    const resolvedProperties = computed<Schemas["properties"] | undefined>(() => {
        // try to resolve the type from local schema
        // IE: when only one schema is available take it and run with it
        if (resolvedLocalSchema.value?.properties) {
            return resolvedLocalSchema.value.properties
        }

        // if there is more than one schema valid, try to find common properties
        // to all the schemas to help user narrow down the schema they want
        if(resolvedTypes.value.length > 1){
            const schemas = resolvedSchemas.value;

            // find properties with the same key and list their keys
            const properties = Object.keys(schemas[0].properties).filter((key) => {
                return schemas.every((schema) => schema.properties[key] !== undefined);
            }).reduce((acc, key) => {
                // check if the properties are the same when they are serialized
                if (schemas.every((schema) => {
                    return isEqual(schemas[0].properties[key], schema.properties[key])
                })) {
                    // if they are we can safely display them
                    acc[key] = schemas[0].properties[key];
                }
                return acc;
            }, {} as Record<string, any>);

            if(dataTypes.value.length > 1){
                properties["data"] = {
                    type: "object",
                    // this is to force the data field to be visible
                    // and TaskComplex and therefore make the data type
                    // appear without a border
                    $ref: "#/definitions/",
                }
            }

            return properties;
        }

        return undefined;
    });

    const dataTypes = computed(() => {
        const types = new Set<string>();
        for(const schema of resolvedSchemas.value){
            const dataResolved = schema.properties?.data?.$ref
                ? getValueAtJsonPath(fullSchema.value, schema.properties?.data?.$ref)
                : schema.properties?.data;
            const typeConst = dataResolved?.properties?.type?.const
            if(typeConst){
                types.add(typeConst);
            }
        }
        return Array.from(types);
    });

    const dataTypesMap = computed(() => dataTypes.value.length > 1 ? {
        data: dataTypes.value
    } : {});

    provide(DATA_TYPES_MAP_INJECTION_KEY, dataTypesMap)

    watch([selectedTaskType, fullSchema], ([task]) => {
        if (task) {
            if(isPlugin.value){
                pluginsStore.updateDocumentation(taskModel.value as Parameters<typeof pluginsStore.updateDocumentation>[0]);
            }
        }
    }, {immediate: true});

    function onTaskInput(val: PartialNoCodeElement | undefined) {
        taskModel.value = val;
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
        const value: PartialNoCodeElement = {
            type: selectedTaskType.value ?? ""
        };

        onTaskInput(value);
    }

    const miscStore = useMiscStore();
    const hash = computed(() => miscStore.configs?.pluginsHash ?? 0);

    const onTaskEditorClick = inject(ON_TASK_EDITOR_CLICK_INJECTION_KEY, (elt?: PartialNoCodeElement) => {
        if(isPlugin.value && elt?.type){
            pluginsStore.updateDocumentation({cls: elt.type, version: elt.version, hash: hash.value});
        }else{
            pluginsStore.updateDocumentation(); 
        }
    });
</script>

<style scoped lang="scss">
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
