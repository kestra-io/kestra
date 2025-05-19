<template>
    <el-form-item class="tabs-wrapper">
        <el-tabs v-model="selectedSchema" @tab-change="onSelectType">
            <el-tab-pane
                v-for="schema in schemaOptions"
                :key="schema.label"
                :label="schema.label"
                :name="schema.value"
            />
        </el-tabs>
    </el-form-item>
    <el-form label-position="top" v-if="selectedSchema">
        <component
            :is="`task-${currentSchemaType}`"
            v-if="currentSchema"
            :model-value="modelValue"
            :schema="currentSchema"
            :properties="currentSchema?.properties"
            :definitions="definitions"
            @update:model-value="onInput"
        />
    </el-form>
</template>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
        emits: ["update:modelValue"],
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
        methods: {
            onSelectType(value) {
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
                    this.onInput(defaultValues);
                }
            },
        },
        computed: {
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
            schemaOptions() {
                // find the part of the prefix to schema references that is common to all schemas
                const schemaRefsArray = this.schemas
                    .map((schema) => schema.$ref?.split("/").pop() ?? schema.type)
                    .filter((schemaRef) => schemaRef)
                    .map((schemaRef) => schemaRef.split("."))

                const commonPart = schemaRefsArray[0]
                    .filter((schemaRef, index) => schemaRefsArray.every((item) => item[index] === schemaRef))
                    .map((schemaRef) => `${schemaRef}.`)
                    .join("");

                // remove the common part from all schema ids
                return this.schemas.map((schema) => {
                    /** @type string */
                    const schemaRef = schema.$ref
                        ? schema.$ref.split("/").pop()
                        : schema.type;

                    const lastPartOfValue = schemaRef.slice(
                        commonPart.length,
                    );

                    return {
                        label: lastPartOfValue.capitalize(),
                        value: schemaRef,
                        id: lastPartOfValue.toLowerCase(),
                    };
                });
            },
        },
    };
</script>

<style lang="scss" scoped>
.el-form {
    width: 100%;
}

.tabs-wrapper {
    .el-tabs {
        width: 100%;
    }

    :deep(.el-tabs__item) {
        padding: 0 8px;
        color: var(--ks-content-tertiary);
        font-size: 14px;

        &.is-active {
            color: var(--ks-content-link);
        }

        &:hover {
            color: var(--ks-content-link-hover);
        }
    }

    :deep(.el-tabs__active-bar) {
        height: 2px;
        background-color: var(--ks-content-link) !important;
    }
}
</style>