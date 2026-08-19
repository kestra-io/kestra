<template>
    <div v-if="!hideRunButton && playgroundStore.enabled && isTask && taskModel?.id && !navStack.length" class="flow-playground">
        <PlaygroundRunTaskButton :taskId="taskModel?.id" />
    </div>

    <FieldNavBreadcrumb
        v-if="navStack.length"
        :frames="navStack"
        :rootLabel="rootLabel"
        @navigate="onCrumb"
        @back="fieldNav.pop"
    />

    <KsForm v-if="isTaskDefinitionBasedOnType && !navStack.length" labelPosition="top">
        <KsFormItem>
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
        </KsFormItem>
    </KsForm>
    <div @click="() => onTaskEditorClick(taskModel)">
        <TaskObject
            v-ks-loading="isLoading || isPluginSchemaLoading"
            v-if="!navStack.length && (selectedTaskType || !isTaskDefinitionBasedOnType) && schema"
            name="root"
            :modelValue="taskModel"
            @update:model-value="onTaskInput"
            :schema
            :properties
            filterType
        />
        <TaskObjectField
            v-else-if="navCurrent"
            :key="navCurrent.path"
            :schema="navCurrent.schema"
            :rootOverride="navCurrent.path"
            :fieldKey="navCurrent.label"
            :task="taskModel"
            :frameRoot="true"
            v-model="frameValue"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, onActivated, provide, ref, toRaw, watch} from "vue"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import TaskObject from "./tasks/TaskObject.vue"
    import TaskObjectField from "./tasks/TaskObjectField.vue"
    import PluginSelect from "../../plugins/PluginSelect.vue"
    import FieldNavBreadcrumb from "./FieldNavBreadcrumb.vue"
    import {useFieldNavigation} from "../utils/useFieldNavigation"
    import {NoCodeElement, Schemas} from "../utils/types"
    import get from "lodash/get"
    import set from "lodash/set"
    import cloneDeep from "lodash/cloneDeep"
    import {
        FIELDNAME_INJECTION_KEY, PARENT_PATH_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
        DATA_TYPES_MAP_INJECTION_KEY,
        ON_TASK_EDITOR_CLICK_INJECTION_KEY,
        FIELD_NAV_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY,
        PLUGIN_DEFAULTS_INJECTION_KEY,
    } from "../injectionKeys"
    import {removeNullAndUndefined} from "../utils/cleanUp"
    import {removeRefPrefix, usePluginsStore} from "../../../stores/plugins"
    import {usePlaygroundStore} from "../../../stores/playground"
    import {getValueAtJsonPath, resolve$ref} from "../../../utils/utils"
    import PlaygroundRunTaskButton from "../../inputs/PlaygroundRunTaskButton.vue"
    import isEqual from "lodash/isEqual"
    import {useMiscStore} from "override/stores/misc"

    defineOptions({
        name: "TaskEditor",
        inheritAttrs: false,
    })

    const modelValue = defineModel<string>()

    defineProps<{
        hideRunButton?: boolean
    }>()

    const pluginsStore = usePluginsStore()
    const playgroundStore = usePlaygroundStore()

    type PartialNoCodeElement = Partial<NoCodeElement>;

    const taskModel = ref<PartialNoCodeElement | undefined>({})
    const selectedTaskType = ref<string>()
    const isLoading = ref(false)

    const fieldNav = useFieldNavigation()
    provide(FIELD_NAV_INJECTION_KEY, fieldNav)
    const {stack: navStack, current: navCurrent} = fieldNav

    const fullSource = inject(FULL_SOURCE_INJECTION_KEY, ref(""))
    const pluginDefaultsForType = computed<Record<string, unknown>>(() => {
        const type = selectedTaskType.value || taskModel.value?.type
        if (!type) return {}
        let parsed: any
        try {
            parsed = YAML_UTILS.parse(fullSource.value)
        } catch {
            return {}
        }
        const defaults = parsed?.pluginDefaults
        if (!Array.isArray(defaults)) return {}
        const merged: Record<string, unknown> = {}
        for (const entry of defaults) {
            if (entry?.type && (type === entry.type || type.startsWith(`${entry.type}.`)) && entry.values) {
                Object.assign(merged, entry.values)
            }
        }
        return merged
    })
    provide(PLUGIN_DEFAULTS_INJECTION_KEY, pluginDefaultsForType)

    const rootLabel = computed(() =>
        taskModel.value?.id
        || selectedTaskType.value?.split(".").pop()
        || "task",
    )

    const frameValue = computed({
        get: () => (navCurrent.value ? get(taskModel.value, navCurrent.value.path) : undefined),
        set: (value) => {
            if (!navCurrent.value) return
            const next = cloneDeep(toRaw(taskModel.value) ?? {})
            set(next as Record<string, any>, navCurrent.value.path, value)
            onTaskInput(next)
        },
    })

    function onCrumb(index: number) {
        if (index < 0) fieldNav.reset()
        else fieldNav.popTo(index)
    }

    watch(selectedTaskType, () => fieldNav.reset())

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "")
    const fieldName = inject(FIELDNAME_INJECTION_KEY, undefined)

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))

    const isTask = computed(() => ["task", "tasks"].includes(parentPath.split(".").pop() ?? ""))

    const isPlugin = computed(() => {
        return parentPath !== "inputs"
    })

    const schemaAtBlockPath = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value))
    const isTaskDefinitionBasedOnType = computed(() => {
        const firstAnyOf = Array.isArray(schemaAtBlockPath.value?.anyOf) ? schemaAtBlockPath.value?.anyOf[0] : undefined
        if (!firstAnyOf) return false
        if(firstAnyOf.properties){
            return firstAnyOf?.properties?.type !== undefined
        }
        if(Array.isArray(firstAnyOf.allOf)){
            return firstAnyOf.allOf.some((item: any) => {
                return resolve$ref(fullSchema.value, item)
                    .properties?.type !== undefined
            })
        }
        return true
    })

    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => selectedTaskType.value ? `#/definitions/${resolvedType.value}` : blockSchemaPath.value))

    watch(modelValue, (v) => {
        if (!v) {
            taskModel.value = {}
            selectedTaskType.value = undefined
        } else {
            setup()
        }
    }, {immediate: true})

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<{
        definitions: Record<string, any>,
        $ref: string,
    }>({
        definitions: {},
        $ref: "",
    }))

    const properties = computed(() => {
        if(!resolvedProperties.value){
            return undefined
        }

        const updatedProperties = {...resolvedProperties.value}

        if (isTaskDefinitionBasedOnType.value) {
            delete updatedProperties["type"]
        }

        if(!updatedProperties?.id && (parentPath.endsWith("task")
            || parentPath.endsWith("tasks")
            || parentPath.endsWith("triggers"))){
            updatedProperties["id"] = {
                type: "string",
                $required: true,
            }
        }

        return updatedProperties
    })

    function setup() {
        taskModel.value = YAML_UTILS.parse<PartialNoCodeElement>(modelValue.value) ?? {}
        selectedTaskType.value = taskModel.value?.type
    }

    onActivated(() => {
        if(selectedTaskType.value && parentPath !== "inputs"){
            pluginsStore.updateDocumentation({cls: selectedTaskType.value, ...taskModel.value})
        }
    })

    const fieldDefinition = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value))

    const typeMap = computed<Record<string, string[]>>(() => {
        if (fieldDefinition.value?.anyOf) {
            const f = fieldDefinition.value.anyOf.reduce((acc: Record<string, string[]>, item: any) => {
                if (item.$ref) {
                    const resolvedItem = getValueAtJsonPath(fullSchema.value, item.$ref)
                    if (resolvedItem?.allOf) {
                        let type = "", refValue
                        for (const subItem of resolvedItem.allOf) {
                            if (subItem.properties?.type?.const) {
                                type = subItem.properties.type.const
                            }
                            if (subItem.$ref) {
                                refValue = removeRefPrefix(subItem.$ref)
                            }
                        }
                        if (type && refValue) {
                            acc[type] = acc[type] || []
                            acc[type].push(refValue)
                        }
                    }

                    const typeField = resolvedItem?.properties?.type
                    if(!typeField){
                        return acc
                    }

                    if(typeField.enum){
                        for(const typeAsEnum of typeField.enum){
                            acc[typeAsEnum] = acc[typeAsEnum] || []
                            acc[typeAsEnum].push(removeRefPrefix(item.$ref))
                        }
                    }

                    const typeAsConst = typeField?.const

                    if (typeAsConst) {
                        acc[typeAsConst] = acc[typeAsConst] || []
                        acc[typeAsConst].push(removeRefPrefix(item.$ref))
                    }
                }
                return acc

            }, {})

            return f
        }

        return {}
    })

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}))

    const resolvedTypes = computed<string[]>(() => {
        return typeMap.value[selectedTaskType.value ?? ""] || []
    })

    const resolvedSchemas = computed(() => {
        return resolvedTypes.value.map((type) => definitions.value?.[type])
    })

    const dataTypes = computed(() => {
        const types = new Set<string>()
        for(const s of resolvedSchemas.value){
            const dataResolved = s?.properties?.data?.$ref
                ? getValueAtJsonPath(fullSchema.value, s.properties.data.$ref)
                : s?.properties?.data
            const typeConst = dataResolved?.properties?.type?.const
            if(typeConst){
                types.add(typeConst)
            }
        }
        return Array.from(types)
    })

    const versionedSchema = ref<Schemas|undefined>()
    const isPluginSchemaLoading = ref(false)

    watch([selectedTaskType, resolvedTypes], async ([val, types]) => {
        if(types.length > 1 && val && dataTypes.value.length <= 1){
            isPluginSchemaLoading.value = true
            try{
                const {schema} = await pluginsStore.load({
                    cls: val,
                    version: taskModel.value?.version,
                })
                versionedSchema.value = schema?.properties
            } catch {
                versionedSchema.value = undefined
            } finally {
                isPluginSchemaLoading.value = false
            }
        } else {
            versionedSchema.value = undefined
        }
    }, {immediate: true})

    const resolvedType = computed<string>(() => {
        if(resolvedTypes.value.length > 1 && selectedTaskType.value){
            const dataType = taskModel.value?.data?.type
            if(dataType){
                for(const typeLocal of resolvedTypes.value){
                    const schema = definitions.value?.[typeLocal]
                    const dataResolved = schema.properties?.data?.$ref
                        ? getValueAtJsonPath(fullSchema.value, schema.properties?.data.$ref)
                        : schema.properties?.data
                    const typeConst = dataResolved?.properties?.type?.const
                    if(typeConst === dataType){
                        return typeLocal
                    }
                }
            }
        }

        return resolvedTypes.value
            ? (resolvedTypes.value.length === 1
                ? resolvedTypes.value[0]
                : selectedTaskType.value ?? "")
            : ""
    })

    const REQUIRED_FIELDS = ["id", "data"]

    const schema = computed(() => {
        const localSchema = resolvedLocalSchema.value
        if(isTaskDefinitionBasedOnType.value){
            localSchema.required = localSchema.required ?? []
            for(const field of REQUIRED_FIELDS){
                if(!localSchema.required.includes(field) && resolvedProperties.value?.[field]){
                    localSchema.required.push(field)
                }
            }
        }
        return localSchema
    })

    const resolvedLocalSchema = computed(() => {
        return versionedSchema.value ?? (isTaskDefinitionBasedOnType.value
            ? definitions.value?.[resolvedType.value] ?? {}
            : schemaAtBlockPath.value)
    })

    const resolvedProperties = computed<Schemas["properties"] | undefined>(() => {
        if (resolvedLocalSchema.value?.properties) {
            return resolvedLocalSchema.value.properties
        }

        if(resolvedTypes.value.length > 1){
            const schemas = resolvedSchemas.value

            const commonProps = Object.keys(schemas[0].properties).filter((key) => {
                return schemas.every((s) => s.properties[key] !== undefined)
            }).reduce((acc, key) => {
                if (schemas.every((s) => {
                    return isEqual(schemas[0].properties[key], s.properties[key])
                })) {
                    acc[key] = schemas[0].properties[key]
                }
                return acc
            }, {} as Record<string, any>)

            if(dataTypes.value.length > 1){
                commonProps["data"] = {
                    type: "object",
                    $ref: "#/definitions/",
                }
            }

            return commonProps
        }

        return undefined
    })

    const dataTypesMap = computed(() => dataTypes.value.length > 1 ? {
        data: dataTypes.value,
    } : {})

    provide(DATA_TYPES_MAP_INJECTION_KEY, dataTypesMap)

    const miscStore = useMiscStore()
    const hash = computed(() => miscStore.configs?.pluginsHash ?? 0)

    watch([selectedTaskType, fullSchema], ([task]) => {
        if (task) {
            if(isPlugin.value){
                pluginsStore.updateDocumentation({cls: task, version: taskModel.value?.version, hash: hash.value})
            }
        }
    }, {immediate: true})

    function onTaskInput(val: PartialNoCodeElement | undefined) {
        taskModel.value = val
        if(fieldName){
            val = {
                [fieldName]: val,
            }
        }

        modelValue.value = YAML_UTILS.stringify(removeNullAndUndefined(toRaw(val)))
    }

    function onTaskTypeSelect() {
        const value: PartialNoCodeElement = {
            type: selectedTaskType.value ?? "",
        }

        onTaskInput(value)
    }

    const onTaskEditorClick = inject(ON_TASK_EDITOR_CLICK_INJECTION_KEY, (elt?: PartialNoCodeElement) => {
        if(isPlugin.value && elt?.type){
            pluginsStore.updateDocumentation({cls: elt.type, version: elt.version, hash: hash.value})
        }else{
            pluginsStore.updateDocumentation()
        }
    })
</script>

<style scoped lang="scss">
    .type-div {
        display: flex;
        text-transform: lowercase;
        align-items: center;
        gap: 0.25rem;
        font-weight: 600;
        .asterisk {
            color: var(--ks-status-error);
        }
        code {
            color: var(--ks-text-primary);
        }
    }

    .flow-playground{
        display: flex;
        justify-content: end;
    }
</style>
