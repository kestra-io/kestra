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
            :is="`task-${currentSchemaType}`"
            v-if="currentSchema"
            :model-value="modelValue"
            :schema="currentSchema"
            :properties="Object.fromEntries(filteredProperties)"
            :definitions="definitions"
            @update:model-value="onAnyOfInput"
        />
    </el-form>
</template>

<script>
    import {mapState} from "vuex";
    import Task from "./Task";
    import {TaskIcon} from "@kestra-io/ui-libs";

    export default {
        components: {
            TaskIcon,
        },
        inheritAttrs: false,
        mixins: [Task],
        emits: ["update:modelValue", "any-of-type"],
        data() {
            return {
                isOpen: false,
                schemas: [],
                selectedSchema: undefined,
            };
        },
        created() {
            this.schemas = this.schema?.anyOf ?? [];

            const schema = this.schemaOptions.find((item) =>
                typeof this.modelValue === "string"
                    ? item.id === "string"
                    : item.id === this.modelValue?.type,
            );
            this.onSelectType(schema?.value || this.schemaOptions[0]?.value);
        },
        watch: {
            constantType(val) {
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
        },

        methods: {
            onSelectType(value) {
                if(this.selectedSchema) this.$emit("any-of-type", value);
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
            },
            onAnyOfInput(value) {
                if(this.constantType?.length && typeof value === "object") {
                    value.type = this.constantType;
                }
                this.onInput(value);
            },
        },

        computed: {
            ...mapState("plugin", ["icons"]),
            constantType() {
                return this.currentSchema?.properties?.type?.const;
            },
            filteredProperties() {
                return this.currentSchema?.properties ? Object.entries(this.currentSchema.properties).filter(([key, schema]) => {
                    return !(key === "type" && schema?.const);
                }) : [];
            },
            currentSchema() {
                return (
                    this.definitions[this.selectedSchema] ??
                    this.schemaByType[this.selectedSchema]
                );
            },
            schemaByType() {
                return this.schemas.reduce((acc, schema) => {
                    acc[schema.type] = schema;
                    return acc;
                }, {});
            },
            currentSchemaType() {
                return this.selectedSchema ? this.getType(this.currentSchema) : undefined;
            },
            isSelectingPlugins() {
                return this.schemas.some((schema) => (schema.$ref?.split("/").pop() ?? schema.type).includes("io.kestra."));
            },
            schemaOptions() {
                // find the part of the prefix to schema references that is common to all schemas
                const schemaRefsArray = this.schemas
                    .map((schema) => schema.$ref?.split("/").pop() ?? schema.type)
                    .filter((schemaRef) => schemaRef)
                    .map((schemaRef) => schemaRef.split("."))

                let mismatch = false
                const commonPart = schemaRefsArray[0]
                    .filter((schemaRef, index) => {
                        if(!mismatch && schemaRefsArray.every((item) => item[index] === schemaRef)){
                            return true;
                        } else {
                            mismatch = true;
                            return false;
                        }
                    })
                    .map((schemaRef) => `${schemaRef}.`)
                    .join("");

                // remove the common part from all schema ids
                return [
                    ...this.required ? [] : [{
                        label: "<Reset>",
                        value: "",
                        id: "<Reset>",
                    }],
                    ...this.schemas.map((schema) => {
                        const schemaRef = schema.$ref
                            ? schema.$ref.split("/").pop()
                            : schema.type;

                        const cleanSchemaRef = schemaRef.replace(/-\d+$/, "");

                        const lastPartOfValue = cleanSchemaRef.slice(
                            commonPart.length,
                        )

                        return {
                            label: lastPartOfValue.capitalize(),
                            value: schemaRef,
                            id: cleanSchemaRef,
                        };
                    })];
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