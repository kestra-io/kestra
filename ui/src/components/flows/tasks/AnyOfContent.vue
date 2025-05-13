<template>
    <el-form-item>
        <el-select :model-value="selectedSchema" @update:model-value="onSelectType">
            <el-option
                v-for="schema in schemaOptions"
                :key="schema.label"
                :label="schema.label"
                :value="schema.value"
            />
        </el-select>
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
    import {BREADCRUMB_INJECTION_KEY} from "../../code/injectionKeys";

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

            this.onSelectType(schema?.value);
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
        inject:{
            breadcrumbs: {from: BREADCRUMB_INJECTION_KEY}
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
                return this.schemas.map((schema) => {
                    const label = schema.$ref
                        ? schema.$ref.split("/").pop()
                        : schema.type;
                    return {
                        label: label.capitalize(),
                        value: label,
                        id: label.split(".").pop().toLowerCase(),
                    };
                });
            },
        },
    };
</script>
