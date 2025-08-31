<template>
    <el-form label-position="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[fieldKey, fieldSchema] in protectedRequiredProperties" :key="fieldKey">
                <TaskWrapper :merge>
                    <template #tasks>
                        <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                    </template>
                </TaskWrapper>
            </template>

            <el-collapse v-model="activeNames" v-if="requiredProperties.length && (optionalProperties?.length || deprecatedProperties?.length || connectionProperties?.length)" class="collapse">
                <el-collapse-item name="connection" v-if="connectionProperties?.length" :title="$t('no_code.sections.connection')">
                    <template v-for="[fieldKey, fieldSchema] in connectionProperties" :key="fieldKey">
                        <TaskWrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </TaskWrapper>
                    </template>
                </el-collapse-item>
                <el-collapse-item name="optional" v-if="optionalProperties?.length" :title="$t('no_code.sections.optional')">
                    <template v-for="[fieldKey, fieldSchema] in optionalProperties" :key="fieldKey">
                        <TaskWrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </TaskWrapper>
                    </template>
                </el-collapse-item>

                <el-collapse-item name="deprecated" v-if="deprecatedProperties?.length" :title="$t('no_code.sections.deprecated')">
                    <template v-for="[fieldKey, fieldSchema] in deprecatedProperties" :key="fieldKey">
                        <TaskWrapper>
                            <template #tasks>
                                <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                            </template>
                        </TaskWrapper>
                    </template>
                </el-collapse-item>
            </el-collapse>
        </template>

        <template v-else>
            <task-dict
                :model-value="modelValue"
                :task="task"
                @update:model-value="
                    (value) => $emit('update:modelValue', value)
                "
                :root="root"
                :schema="schema"
                :required="required"
                :definitions="definitions"
            />
        </template>
    </el-form>
</template>

<script setup>
    import TaskDict from "./TaskDict.vue";
    import TaskWrapper from "./TaskWrapper.vue";
    import TaskObjectField from "./TaskObjectField.vue";

    defineEmits(["update:modelValue"]);
</script>

<script>
    import Task from "./Task";

    const FIRST_FIELDS = ["id", "forced", "on", "type"];

    function sortProperties(properties, required) {
        if(!properties.length) {
            return [];
        }
        return properties.sort((a, b) => {
            if (FIRST_FIELDS.includes(a[0])) {
                return -1;
            } else if (FIRST_FIELDS.includes(b[0])) {
                return 1;
            }

            const aRequired = (required || []).includes(
                a[0],
            );
            const bRequired = (required || []).includes(
                b[0],
            );

            if (aRequired && !bRequired) {
                return -1;
            } else if (!aRequired && bRequired) {
                return 1;
            }

            const aDefault = "default" in a[1];
            const bDefault = "default" in b[1];

            if (aDefault && !bDefault) {
                return 1;
            } else if (!aDefault && bDefault) {
                return -1;
            }

            return a[0].localeCompare(b[0]);
        })
    }

    export default {
        inheritAttrs: false,
        name: "TaskObject",
        mixins: [Task],
        props: {
            properties: {
                type: Object,
                default: () => ({}),
            },
            merge: {type: Boolean, default: false},
            metadataInputs: {type: Boolean, default: false}
        },
        data() {
            return {
                activeNames: [],
            };
        },
        computed: {
            filteredProperties() {
                return this.properties ? Object.entries(this.properties).filter(([key, value]) => {
                    return !(key === "type") && !Array.isArray(value);
                }) : [];
            },
            sortedProperties() {
                return sortProperties(this.filteredProperties, this.schema?.required);
            },
            requiredProperties() {
                return this.merge ? this.sortedProperties : this.sortedProperties.filter(([p,v]) => v && this.isRequired(p));
            },
            protectedRequiredProperties(){
                return this.requiredProperties.length ? this.requiredProperties : this.sortedProperties;
            },
            optionalProperties() {
                return this.merge ? [] : this.sortedProperties.filter(([p,v]) => v && !this.isRequired(p) && !v.$deprecated && v.$group !== "connection");
            },
            deprecatedProperties() {
                return this.merge ? [] : this.sortedProperties.filter(([k,v]) => v && v.$deprecated && this.modelValue[k] !== undefined);
            },
            connectionProperties() {
                return this.merge ? [] : this.sortedProperties.filter(([p,v]) => v && v.$group === "connection" && !this.isRequired(p));
            },
        },
        methods: {
            onObjectInput(propertyName, value) {
                const currentValue = this.modelValue || {};
                currentValue[propertyName] = value;
                this.onInput(currentValue);
            },
            isNestedProperty(key) {
                return key.includes(".") ||
                    ["interval", "maxInterval", "minInterval", "type"].includes(key);
            },
            fieldProps(key, schema) {
                return {
                    modelValue: this.modelValue?.[key],
                    "onUpdate:modelValue": (value) => {
                        this.onObjectInput(key, value);
                    },
                    root: this.root,
                    fieldKey: key,
                    task: this.modelValue,
                    schema: schema,
                    definitions: this.definitions,
                    required: this.schema.required,
                };
            },
        },

    };
</script>

<style lang="scss">
    .el-form-item__content {
        .el-form-item {
            width: 100%;
        }
    }

    .el-popper.singleton-tooltip {
        max-width: 300px !important;
        background: var(--ks-tooltip-background);
    }
</style>

<style lang="scss" scoped>
@import "../../code/styles/code.scss";

.el-form-item {
    width: 100%;
    margin-bottom: 0;
    > :deep(.el-form-item__label) {
        width: 100%;
        display: flex;
        align-items: center;
        padding: 0;
    }
}

.inline-wrapper {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    min-width: 0;

    .inline-start {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
        flex: 1 1 auto;
    }

    .label {
        color: var(--ks-content-primary);
        min-width: 0;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        font-weight: 600;
    }

    .type-tag {
        background-color: var(--ks-tag-background-active);
        color: var(--ks-tag-content);
        font-size: 12px;
        line-height: 20px;
        padding: 0 8px;
        padding-bottom: 2px;
        border-radius: 8px;
        text-transform: capitalize;
    }

    .information-icon {
        color: var(--ks-content-secondary);
        cursor: pointer;
    }
}
</style>
