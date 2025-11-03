<template>
    <el-form-item :class="{'radio-wrapper':!isSelectingPlugins}">
        <el-select
            v-if="isSelectingPlugins"
            v-model="selectedSchema"
            filterable
        >
            <el-option
                v-for="item in schemaOptions"
                :key="item.value"
                :label="item.id"
                :value="item.value"
            />
        </el-select>
        <el-radio-group v-else v-model="selectedSchema" @change="onSelectType">
            <el-radio
                v-for="radioSchema in schemaOptions"
                :key="radioSchema.value"
                :value="radioSchema.value"
            >
                {{ radioSchema.label }}
            </el-radio>
        </el-radio-group>
    </el-form-item>
    <el-form labelPosition="top" v-if="selectedSchema">
        <component
            :is="currentSchemaType"
            v-if="currentSchema"
            :modelValue="modelValue"
            :schema="currentSchema"
            :properties="Object.fromEntries(filteredProperties)"
            @update:model-value="onAnyOfInput"
            merge
        />
    </el-form>
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted, nextTick, inject} from "vue";
    import {Schema} from "./getTaskComponent";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {SCHEMA_DEFINITIONS_INJECTION_KEY} from "../../injectionKeys";
    import {useBlockComponent} from "./useBlockComponent";

    const props = defineProps<{
        schema: Schema,
        required?: boolean
    }>();

    defineOptions({inheritAttrs: false});

    const model = defineModel<any>()

    const emit = defineEmits(["update:selectedSchema"]);

    const selectedSchema = ref<string>();
    const delayedSelectedSchema = ref<string>();
    const finishedMounting = ref(false);

    function consolidateAllOfSchemas(schema: Schema, definitions: Record<string, Schema>) {
        if (schema?.allOf?.length) {
            return {
                ...schema,
                ...schema.allOf.reduce<Schema>((acc, item) => {
                    if(!acc.required){
                        return acc;
                    }
                    if (item.$ref) {
                        const refSchema = definitions[item.$ref.split("/").pop() ?? "---"];
                        if (refSchema) {
                            return {
                                ...acc,
                                required: [
                                    ...acc.required,
                                    ...(refSchema.required ?? [])
                                ],
                                properties: {
                                    ...acc.properties,
                                    ...refSchema.properties,
                                }
                            };
                        }
                    } else {
                        return {
                            ...acc,
                            required: [
                                ...acc.required,
                                ...(item.required ?? [])
                            ],
                            properties: {
                                ...acc.properties,
                                ...item.properties,
                            }
                        };
                    }
                    return acc;
                }, {
                    type: "object",
                    properties: {},
                    required: [],
                }),
                
            }
        }
        return schema;
    }

    const schemas = computed(() => {
        if (!props.schema?.anyOf || !Array.isArray(props.schema.anyOf)) return [];
        return props.schema.anyOf.map((schema: Schema) => {
            if (schema.allOf && Array.isArray(schema.allOf)) {
                if (schema.allOf.length === 2 && schema.allOf[0].$ref && !schema.allOf[1].$ref) {
                    return {
                        ...schema.allOf[1],
                        $ref: schema.allOf[0].$ref,
                    };
                }
            }
            return schema;
        });
    });

    const allSchemaSameType = computed(() => {
        if (schemas.value.length < 2) return false;
        const firstType = schemas.value[0].type;
        if(firstType === undefined){
            return false;
        }
        return schemas.value.every((schema: Schema) => schema.type === firstType);
    });

    function makeKey(schema: Schema) {
        if(typeof schema.type === "object"){
            return schema.type.const;
        }
        if(allSchemaSameType.value && schema.items){
            return `${schema.type}.${schema.items.type}${schema.items.format ? `.${schema.items.format}` : ""}`;
        }
        return schema.type;
    }

    const schemaByType = computed(() => {
        return schemas.value.reduce((acc: Record<string, any>, schema: any) => {
            acc[makeKey(schema)] = schema;
            return acc;
        }, {});
    });

    const constantType = computed(() => currentSchema.value?.properties?.type?.const);

    const filteredProperties = computed(() =>
        currentSchema.value?.properties
            ? Object.entries(currentSchema.value.properties).filter(([key, schema]: [string, Schema]) => !(key === "type" && schema?.const))
            : []
    );

    const definitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, computed<Record<string, Schema>>(() => ({})));

    const currentSchema = computed(() => {
        if(!delayedSelectedSchema.value) return
        const rawSchema = definitions.value[delayedSelectedSchema.value] ?? schemaByType.value[delayedSelectedSchema.value];
        return consolidateAllOfSchemas(rawSchema, definitions.value);
    });

    const {getBlockComponent} = useBlockComponent();

    const currentSchemaType = computed(() =>
        delayedSelectedSchema.value ? getBlockComponent.value(currentSchema.value) : undefined
    );

    const isSelectingPlugins = computed(() => schemas.value.length > 4);

    const schemaOptions = computed<{label: string, value: string, id: string}[]>(() => {
        // if all schemas are of type array we have to
        // look at the type of their items to differentiate them
        if(allSchemaSameType.value){
            return schemas.value.map((schema: any) => {
                const itemsType = schema.items?.format ?? schema.items?.type;

                return {
                    label: itemsType.charAt(0).toUpperCase() + itemsType.slice(1),
                    value: makeKey(schema),
                    id: itemsType,
                };
            })
        }

        if (!schemas.value?.length || !definitions.value) return [];
        const schemaRefsArray = (schemas.value as {$ref?: string, type: string}[])
            .map((schema) => schema.$ref?.split("/").pop() ?? schema.type)
            .filter((schemaRef) => schemaRef !== undefined)
            .map((schemaRef) => typeof definitions.value[schemaRef]?.type === "object" ? definitions.value[schemaRef]?.type?.const : schemaRef)
            .map((schemaRef: string) => schemaRef.split("."));

        let mismatch = false;
        const commonPart = schemaRefsArray[0]
            ?.filter((schemaRef: string, index: number) => {
                if (!mismatch && schemaRefsArray.every((item: string[]) => item[index] === schemaRef)) {
                    return true;
                } else {
                    mismatch = true;
                    return false;
                }
            })
            .map((schemaRef: string) => `${schemaRef}.`)
            .join("");
        

        
        return schemas.value.map((schema: any) => {
            const schemaRef = schema.$ref
                ? schema.$ref.split("/").pop()
                : schema.type;

            if (!schemaRef) {
                return {
                    label: "Unknown Schema",
                    value: "",
                    id: "",
                };
            }

            const cleanSchemaRef = schemaRef.replace(/-\d+$/, "");
            const lastPartOfValue = cleanSchemaRef.slice(commonPart.length);

            return {
                label: lastPartOfValue.charAt(0).toUpperCase() + lastPartOfValue.slice(1),
                value: schemaRef,
                id: cleanSchemaRef,
            };
        }).filter((schema: any) => schema.value !== undefined);
    });

    watch(() => constantType.value, (val) => {
        if (!finishedMounting.value) return;
        if (!val) {
            onInput(undefined);
            return;
        }
        if (model.value) {
            for (const key in model.value) {
                if (key !== "type" && !filteredProperties.value?.some(([k]) => k === key)) {
                    delete model.value[key];
                }
            }
        }
        onAnyOfInput(model.value || {type: val});
    });

    watch(selectedSchema, (val) => {
        emit("update:selectedSchema", val);
        nextTick(() => {
            delayedSelectedSchema.value = val;
        });
    });

    onMounted(() => {
        const schema = schemaOptions.value.find((item: any) =>
            item.value === model.value?.type ||
            (typeof model.value === "string" && item.value === "string") ||
            (typeof model.value === "number" && item.value === "integer") ||
            (Array.isArray(model.value) && item.value === "array") ||
            (typeof model.value === "object" && item.value === "object") ||
            (Array.isArray(model.value) && typeof model.value[0] === "number" && item.value === "array.number") ||
            (Array.isArray(model.value) && typeof model.value[0] === "string" && !isNaN(Date.parse(item.value[0])) && item.value === "array.string.date-time") ||
            (Array.isArray(model.value) && typeof model.value[0] === "string" && item.value === "array.string")
        );

        selectedSchema.value = schema?.value;

        if (!selectedSchema.value && schemas.value.length > 0 && props.required) {
            selectedSchema.value = typeof schemas.value[0].type === "object" ? schemas.value[0].type.const : schemas.value[0].type;
        }

        if (schema) {
            onSelectType(schema.value);
        }
        nextTick(() => {
            finishedMounting.value = true;
        });
    });

    // Methods
    function onSelectType(value: string) {
        if (typeof model.value === "string" && (value === "object" || value === "array")) {
            let parsedValue: any = {};
            try {
                parsedValue = YAML_UTILS.parse(model.value) ?? {};
                if (value === "array" && !Array.isArray(parsedValue)) {
                    parsedValue = [parsedValue];
                }
            } catch {
                // ignore invalid yaml
            }
        }
        if (value === "string") {
            if (Array.isArray(model.value) && model.value.length === 1) {
                model.value = model.value[0];
            } else if (typeof model.value !== "string") {
                model.value = YAML_UTILS.stringify(model.value);
            }
        }
        selectedSchema.value = value;
        if (currentSchema.value?.properties && model.value === undefined) {
            const defaultValues: Record<string, any> = {};
            for (let prop in currentSchema.value.properties) {
                if (
                    currentSchema.value.properties[prop].$required &&
                    currentSchema.value.properties[prop].default
                ) {
                    defaultValues[prop] = currentSchema.value.properties[prop].default;
                }
            }
            onInput(defaultValues);
        }
        delayedSelectedSchema.value = value;
    }

    function onAnyOfInput(value: any) {
        if (constantType.value?.length && typeof value === "object") {
            value.type = constantType.value;
        }
        onInput(value);
    }

    function onInput(value: any) {
        model.value = value;
    }

    function resetSelectType() {
        selectedSchema.value = undefined;
        nextTick(() => {
            onInput(undefined);
        });
    }

    // Expose
    defineExpose({
        resetSelectType
    });
</script>

<style scoped lang="scss">
.el-form {
    width: 100%;
}

.radio-wrapper {
    :deep(.el-radio-group) {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        margin-bottom: .5rem;
    }

    :deep(.el-radio) {
        margin-right: 0;
        height: 40px;

        .el-radio__inner {
            width: 24px;
            height: 24px;
            border: 2px solid var(--ks-content-link);
            background: transparent;

            &::after {
                width: 12px;
                height: 12px;
                background-color: var(--ks-content-link);
            }
        }

        &.is-checked {
            .el-radio__label {
                color: var(--ks-content-link);
            }
            .el-radio__inner {
                border-color: var(--ks-content-link);
                background: transparent;
            }
        }

        &:hover {
            .el-radio__label {
                color: var(--ks-content-link-hover);
            }
            .el-radio__inner {
                border-color: var(--ks-content-link-hover);
            }
        }
    }
}
</style>