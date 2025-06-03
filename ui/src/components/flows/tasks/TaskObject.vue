<template>
    <el-form label-position="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[fieldKey, fieldSchema] in requiredProperties" :key="fieldKey">
                <template v-if="fieldKey === 'id' || isNestedProperty(fieldKey)">
                    <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                </template>

                <template v-else>
                    <TaskWrapper :merge>
                        <template #tasks>
                            <TaskObjectField v-bind="fieldProps(fieldKey, fieldSchema)" />
                        </template>
                    </TaskWrapper>
                </template>
            </template>

            <el-collapse v-model="activeNames" v-if="optionalProperties?.length || deprecatedProperties?.length" class="collapse">
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
</script>

<script>
    import Task from "./Task";

    function sortProperties(properties, required) {
        if (!properties) {
            return properties;
        }

        return Object.entries(properties)
            .sort((a, b) => {
                if (a[0] === "id" || a[0] === "forced") {
                    return -1;
                } else if (b[0] === "id" || b[0] === "forced") {
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
        emits: ["update:modelValue"],
        data() {
            return {
                activeNames: [],
            };
        },
        computed: {
            sortedProperties() {
                return sortProperties(this.properties, this.schema?.required);
            },
            requiredProperties() {
                return this.merge ? this.sortedProperties : this.sortedProperties.filter(([p,v]) => v && this.isRequired(p));
            },
            optionalProperties() {
                return this.merge ? [] : this.sortedProperties.filter(([p,v]) => v && !this.isRequired(p) && !v.$deprecated);
            },
            deprecatedProperties() {
                return this.merge ? [] : this.sortedProperties.filter(([_,v]) => v && v.$deprecated);
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
                    fieldKey: key,
                    task: this.modelValue,
                    schema: schema,
                    definitions: this.definitions,
                    required: this.schema?.required,
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