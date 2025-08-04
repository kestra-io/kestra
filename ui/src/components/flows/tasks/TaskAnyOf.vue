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
                v-for="schema in schemaOptions"
                :key="schema.label"
                :value="schema.value"
            >
                {{ schema.label }}
            </el-radio>
        </el-radio-group>
    </el-form-item>
    <el-form label-position="top" v-if="selectedSchema">
        <component
            :is="currentSchemaType"
            v-if="currentSchema"
            :model-value="modelValue"
            :schema="currentSchema"
            :properties="Object.fromEntries(filteredProperties)"
            :definitions="definitions"
            @update:model-value="onAnyOfInput"
            merge
        />
    </el-form>
</template>

<script>
    import Task from "./Task";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import getTaskComponent from "./getTaskComponent";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    /**
     * merge allOf schemas if they exist
     * @param schema
     */
    function consolidateAllOfSchemas(schema, definitions) {
        if(schema?.allOf?.length) {
            return {
                ...schema,
                type: "object",
                ...schema.allOf.reduce((acc, item) => {
                    if(item.$ref) {
                        const refSchema = definitions[item.$ref.split("/").pop()];
                        if(refSchema) {
                            return {
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
                    properties: {},
                    required: [],
                })
            }
        }
        return schema;
    }

    export default {
        components: {
            TaskIcon,
        },
        inheritAttrs: false,
        mixins: [Task],
        emits: ["update:modelValue", "update:selectedSchema"],
        data() {
            return {
                isOpen: false,
                selectedSchema: undefined,
                delayedSelectedSchema: undefined,
                finishedMounting: false,
            };
        },
        created() {
            const schema = this.schemaOptions.find((item) =>
                item.value === this.modelValue?.type ||
                (typeof this.modelValue === "string" && item.value === "string") ||
                (typeof this.modelValue === "number" && item.value === "integer") ||
                (Array.isArray(this.modelValue) && item.value === "array") ||
                // this last line needs to stay after the array one.
                // If not, arrays will be detected as objects
                (typeof this.modelValue === "object" && item.value === "object"),
            );

            this.selectedSchema = schema?.value;

            // only default selector to required values
            if(!this.selectedSchema && this.schemas.length > 0 && this.required) {
                this.selectedSchema = this.schemas[0].type;
            }

            if (schema) {
                this.onSelectType(schema.value);
            }
        },
        mounted() {
            this.$nextTick(() => {
                this.finishedMounting = true
            })
        },
        watch: {
            constantType(val) {
                // avoid setting values
                // before user acts on the component
                if(!this.finishedMounting) {
                    return;
                }
                if(!val) {
                    this.onInput(undefined);
                    return;
                }
                // If the constant type changes, we need to update the modelValue
                if(this.modelValue){
                    for(const val in this.modelValue) {
                        if(val !== "type" && !this.filteredProperties?.some(([key]) => key === val)) {
                            delete this.modelValue[val];
                        }
                    }
                }
                this.onAnyOfInput(this.modelValue || {type: val});
            },
            selectedSchema(val) {
                this.$emit("update:selectedSchema", val);
                this.$nextTick(() => {
                    this.delayedSelectedSchema = val;
                });
            },
        },

        methods: {
            onSelectType(value) {
                // When switching form string to object/array,
                // We try to parse the string as YAML
                // If the value is not yaml it has no point on being kept.
                if(typeof this.modelValue === "string" && (value === "object" || value === "array")) {
                    let parsedValue = {}
                    try{
                        parsedValue = YAML_UTILS.parse(this.modelValue) ?? {};
                        if(value === "array" && !Array.isArray(parsedValue)) {
                            parsedValue = [parsedValue];
                        }
                    } catch {
                        // eat an error
                    }

                    this.$emit("update:modelValue", parsedValue);
                }

                if(value === "string") {
                    if(Array.isArray(this.modelValue) && this.modelValue.length === 1) {
                        this.$emit("update:modelValue", this.modelValue[0]);
                    }else{
                        this.$emit("update:modelValue", YAML_UTILS.stringify(this.modelValue));
                    }
                }

                this.selectedSchema = value;
                // Set up default values
                if (
                    this.currentSchema?.properties &&
                    this.modelValue === undefined
                ) {
                    const defaultValues = {};
                    for (let prop in this.currentSchema.properties) {
                        if (
                            this.currentSchema.properties[prop].$required &&
                            this.currentSchema.properties[prop].default
                        ) {
                            defaultValues[prop] =
                                this.currentSchema.properties[prop].default;
                        }
                    }
                    this.onInput(defaultValues)
                }
                this.delayedSelectedSchema = value;
            },
            onAnyOfInput(value) {
                if(this.constantType?.length && typeof value === "object") {
                    value.type = this.constantType;
                }
                this.onInput(value);
            },
            resetSelectType() {
                this.selectedSchema = undefined;
                this.$nextTick(() => {
                    this.onInput(undefined);
                });
            },
        },

        expose: [
            "resetSelectType",
        ],

        computed: {
            schemas() {
                if(!this.schema?.anyOf || !Array.isArray(this.schema.anyOf)) {
                    return [];
                }
                return this.schema.anyOf.map((schema) => {

                    if(schema.allOf && Array.isArray(schema.allOf)) {
                        if(schema.allOf.length === 2 && schema.allOf[0].$ref && !schema.allOf[1].$ref) {
                            return {
                                ...schema.allOf[1],
                                $ref: schema.allOf[0].$ref,
                            };
                        }
                    }

                    return schema;
                });
            },
            constantType() {
                return this.currentSchema?.properties?.type?.const;
            },
            filteredProperties() {
                return this.currentSchema?.properties ? Object.entries(this.currentSchema.properties).filter(([key, schema]) => {
                    return !(key === "type" && schema?.const);
                }) : [];
            },
            currentSchema() {
                const rawSchema = this.definitions[this.delayedSelectedSchema] ?? this.schemaByType[this.delayedSelectedSchema]
                return consolidateAllOfSchemas(rawSchema, this.definitions);
            },
            schemaByType() {
                return this.schemas.reduce((acc, schema) => {
                    acc[schema.type] = schema;
                    return acc;
                }, {});
            },
            currentSchemaType() {
                return this.delayedSelectedSchema ? getTaskComponent(this.currentSchema) : undefined;
            },
            isSelectingPlugins() {
                return this.schemas.length > 4;
            },
            schemaOptions() {
                if (!this.schemas?.length || !this.definitions) {
                    return [];
                }

                // find the part of the prefix to schema references that is common to all schemas
                const schemaRefsArray = this.schemas
                    ?.map((schema) => schema.$ref?.split("/").pop() ?? schema.type)
                    .filter((schemaRef) => schemaRef)
                    .map((schemaRef) => this.definitions[schemaRef]?.type?.const ?? schemaRef)
                    .map((schemaRef) => schemaRef.split("."))

                let mismatch = false
                const commonPart = schemaRefsArray[0]
                    ?.filter((schemaRef, index) => {
                        if(!mismatch && schemaRefsArray.every((item) => item[index] === schemaRef)){
                            return true;
                        } else {
                            mismatch = true;
                            return false;
                        }
                    })
                    .map((schemaRef) => `${schemaRef}.`)
                    .join("");

                return this.schemas.map((schema) => {
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

                    const lastPartOfValue = cleanSchemaRef.slice(
                        commonPart.length,
                    )

                    return {
                        label: lastPartOfValue.capitalize(),
                        value: schemaRef,
                        id: cleanSchemaRef,
                    };
                }).filter((schema) => {
                    return schema.value
                });
            },
        },
    };
</script>

<style lang="scss" scoped>
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