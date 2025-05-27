<template>
    <el-form label-position="top" class="w-100">
        <template v-if="sortedProperties">
            <template v-for="[key, fieldSchema] in requiredProperties" :key="key">
                <template v-if="key === 'id' || isNestedProperty(key)">
                    <TaskObjectField
                        v-model="modelValue[key]"
                        :field-key="key"
                        :schema="fieldSchema"
                        :definitions
                        :task="modelValue"
                    />
                </template>

                <template v-else>
                    <TaskWrapper :merge>
                        <template #tasks>
                            <TaskObjectField
                                v-model="modelValue[key]"
                                :field-key="key"
                                :schema="fieldSchema"
                                :definitions
                                :task="modelValue"
                            />
                        </template>
                    </TaskWrapper>
                </template>
            </template>

            <el-collapse v-model="activeNames" v-if="optionalProperties?.length || deprecatedProperties?.length" class="collapse">
                <el-collapse-item name="optional" v-if="optionalProperties?.length" :title="$t('no_code.sections.optional')">
                    <template v-for="[key, fieldSchema] in optionalProperties" :key="key">
                        <TaskWrapper>
                            <template #tasks>
                                <TaskObjectField
                                    v-model="modelValue[key]"
                                    :field-key="key"
                                    :schema="fieldSchema"
                                    :definitions
                                    :task="modelValue"
                                />
                            </template>
                        </TaskWrapper>
                    </template>
                </el-collapse-item>

                <el-collapse-item name="deprecated" v-if="deprecatedProperties?.length" :title="$t('no_code.sections.deprecated')">
                    <template v-for="[key, fieldSchema] in deprecatedProperties" :key="key">
                        <TaskWrapper>
                            <template #tasks>
                                <TaskObjectField
                                    v-model="modelValue[key]"
                                    :field-key="key"
                                    :schema="fieldSchema"
                                    :definitions
                                    :task="modelValue"
                                />
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

<script>
    import Task from "./Task";
    import TaskDict from "./TaskDict.vue";
    import TaskWrapper from "./TaskWrapper.vue";
    import TaskObjectField from "./TaskObjectField.vue";

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
        components: {
            TaskDict,
            TaskWrapper,
            TaskObjectField,
        },
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
            isNestedProperty(key) {
                return key.includes(".") ||
                    ["interval", "maxInterval", "minInterval", "type"].includes(key);
            },
        },
    };
</script>

<style lang="scss">
    .el-form-item {
        margin-bottom: 1rem;
    }

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
</style>