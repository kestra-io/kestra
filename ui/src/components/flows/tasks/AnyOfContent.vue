<template>
    <el-form-item class="tabs-wrapper">
        <el-tabs v-model="selectedSchema" @tab-change="onSelect">
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
            :is="`task-${getType(currentSchema)}`"
            v-if="currentSchema"
            :model-value="modelValue"
            @update:model-value="onInput"
            :schema="currentSchema"
            :definitions="definitions"
            :properties="currentSchema.properties"
        />
    </el-form>
</template>

<script>
    import Task from "./Task";
    import {mapState} from "vuex";

    export default {
        mixins: [Task],
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
            this.onSelect(schema?.value || this.schemaOptions[0]?.value);
        },
        methods: {
            onSelect(value) {
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
            ...mapState("code", ["breadcrumbs"]),

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

<style lang="scss" scoped>
.tabs-wrapper {
    .el-tabs {
        width: 100%;
    }

    :deep(.el-tabs__header) {
        margin: 0;
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