<template>
    <el-form label-position="top">
        <template v-if="sortedProperties">
            <template v-for="[key, schema] in requiredProperties" :key="key">
                <template v-if="isAnyOf(schema)">
                    <TaskWrapper>
                        <template #tasks>
                            <el-form-item :required="isRequired(key)">
                                <template #label>
                                    <span v-if="getKey(key)" class="label">
                                        {{ getKey(key) }}
                                    </span>
                                    <el-tag
                                        v-if="!isAnyOf(schema)"
                                        disable-transitions
                                        size="small"
                                        class="ms-2 type-tag"
                                    >
                                        {{ getType(schema) }}
                                    </el-tag>
                                    <el-tooltip
                                        v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                        :persistent="false"
                                        :hide-after="0"
                                        effect="light"
                                    >
                                        <template #content>
                                            <markdown
                                                class="markdown-tooltip"
                                                :source="helpText(schema)"
                                            />
                                        </template>
                                        <help class="ms-2" />
                                    </el-tooltip>
                                </template>
                                <component
                                    :is="`task-${getType(schema, key)}`"
                                    :model-value="modelValue?.[key]"
                                    :task="modelValue"
                                    @update:model-value="onObjectInput(key, $event)"
                                    :root="getKey(key)"
                                    :schema="schema"
                                    :required="isRequired(key)"
                                    :definitions="definitions"
                                    class="mt-1 mb-2 wrapper"
                                    v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                                />
                            </el-form-item>
                        </template>
                    </TaskWrapper>
                </template>
                
                <template v-else>
                    <el-form-item :required="isRequired(key)">
                        <template #label>
                            <span v-if="getKey(key)" class="label">
                                {{ getKey(key) }}
                            </span>
                            <el-tag
                                v-if="!isAnyOf(schema)"
                                disable-transitions
                                size="small"
                                class="ms-2 type-tag"
                            >
                                {{ getType(schema) }}
                            </el-tag>
                            <el-tooltip
                                v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                :persistent="false"
                                :hide-after="0"
                                effect="light"
                            >
                                <template #content>
                                    <markdown
                                        class="markdown-tooltip"
                                        :source="helpText(schema)"
                                    />
                                </template>
                                <help class="ms-2" />
                            </el-tooltip>
                        </template>
                        <component
                            :is="`task-${getType(schema, key)}`"
                            :model-value="modelValue?.[key]"
                            :task="modelValue"
                            @update:model-value="onObjectInput(key, $event)"
                            :root="getKey(key)"
                            :schema="schema"
                            :required="isRequired(key)"
                            :definitions="definitions"
                            class="mt-1 mb-2 wrapper"
                            v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                        />
                    </el-form-item>
                </template>
            </template>

            <el-collapse v-model="activeNames" v-if="optionalProperties?.length" class="collapse">
                <el-collapse-item name="optional" :title="$t('no_code.sections.optional')">
                    <template v-for="[key, schema] in optionalProperties" :key="key">
                        <template v-if="isAnyOf(schema)">
                            <TaskWrapper>
                                <template #tasks>
                                    <el-form-item :required="isRequired(key)">
                                        <template #label>
                                            <span v-if="getKey(key)" class="label">
                                                {{ getKey(key) }}
                                            </span>
                                            <el-tag
                                                v-if="!isAnyOf(schema)"
                                                disable-transitions
                                                size="small"
                                                class="ms-2 type-tag"
                                            >
                                                {{ getType(schema) }}
                                            </el-tag>
                                            <el-tooltip
                                                v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                                :persistent="false"
                                                :hide-after="0"
                                                effect="light"
                                            >
                                                <template #content>
                                                    <markdown
                                                        class="markdown-tooltip"
                                                        :source="helpText(schema)"
                                                    />
                                                </template>
                                                <help class="ms-2" />
                                            </el-tooltip>
                                        </template>
                                        <component
                                            :is="`task-${getType(schema, key)}`"
                                            :model-value="modelValue?.[key]"
                                            :task="modelValue"
                                            @update:model-value="onObjectInput(key, $event)"
                                            :root="getKey(key)"
                                            :schema="schema"
                                            :required="isRequired(key)"
                                            :definitions="definitions"
                                            class="mt-1 mb-2 wrapper"
                                            v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                                        />
                                    </el-form-item>
                                </template>
                            </TaskWrapper>
                        </template>
                        
                        <template v-else>
                            <el-form-item :required="isRequired(key)">
                                <template #label>
                                    <span v-if="getKey(key)" class="label">
                                        {{ getKey(key) }}
                                    </span>
                                    <el-tag
                                        v-if="!isAnyOf(schema)"
                                        disable-transitions
                                        size="small"
                                        class="ms-2 type-tag"
                                    >
                                        {{ getType(schema) }}
                                    </el-tag>
                                    <el-tooltip
                                        v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                        :persistent="false"
                                        :hide-after="0"
                                        effect="light"
                                    >
                                        <template #content>
                                            <markdown
                                                class="markdown-tooltip"
                                                :source="helpText(schema)"
                                            />
                                        </template>
                                        <help class="ms-2" />
                                    </el-tooltip>
                                </template>
                                <component
                                    :is="`task-${getType(schema, key)}`"
                                    :model-value="modelValue?.[key]"
                                    :task="modelValue"
                                    @update:model-value="onObjectInput(key, $event)"
                                    :root="getKey(key)"
                                    :schema="schema"
                                    :required="isRequired(key)"
                                    :definitions="definitions"
                                    class="mt-1 mb-2 wrapper"
                                    v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                                />
                            </el-form-item>
                        </template>
                    </template>
                </el-collapse-item>
            </el-collapse>

            <el-collapse v-model="activeNames" v-if="deprecatedProperties?.length" class="collapse">
                <el-collapse-item name="deprecated" :title="$t('no_code.sections.deprecated')">
                    <template v-for="[key, schema] in deprecatedProperties" :key="key">
                        <template v-if="isAnyOf(schema)">
                            <TaskWrapper>
                                <template #tasks>
                                    <el-form-item :required="isRequired(key)">
                                        <template #label>
                                            <span v-if="getKey(key)" class="label">
                                                {{ getKey(key) }}
                                            </span>
                                            <el-tag
                                                v-if="!isAnyOf(schema)"
                                                disable-transitions
                                                size="small"
                                                class="ms-2 type-tag"
                                            >
                                                {{ getType(schema) }}
                                            </el-tag>
                                            <el-tooltip
                                                v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                                :persistent="false"
                                                :hide-after="0"
                                                effect="light"
                                            >
                                                <template #content>
                                                    <markdown
                                                        class="markdown-tooltip"
                                                        :source="helpText(schema)"
                                                    />
                                                </template>
                                                <help class="ms-2" />
                                            </el-tooltip>
                                        </template>
                                        <component
                                            :is="`task-${getType(schema, key)}`"
                                            :model-value="modelValue?.[key]"
                                            :task="modelValue"
                                            @update:model-value="onObjectInput(key, $event)"
                                            :root="getKey(key)"
                                            :schema="schema"
                                            :required="isRequired(key)"
                                            :definitions="definitions"
                                            class="mt-1 mb-2 wrapper"
                                            v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                                        />
                                    </el-form-item>
                                </template>
                            </TaskWrapper>
                        </template>
                        
                        <template v-else>
                            <el-form-item :required="isRequired(key)">
                                <template #label>
                                    <span v-if="getKey(key)" class="label">
                                        {{ getKey(key) }}
                                    </span>
                                    <el-tag
                                        v-if="!isAnyOf(schema)"
                                        disable-transitions
                                        size="small"
                                        class="ms-2 type-tag"
                                    >
                                        {{ getType(schema) }}
                                    </el-tag>
                                    <el-tooltip
                                        v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                        :persistent="false"
                                        :hide-after="0"
                                        effect="light"
                                    >
                                        <template #content>
                                            <markdown
                                                class="markdown-tooltip"
                                                :source="helpText(schema)"
                                            />
                                        </template>
                                        <help class="ms-2" />
                                    </el-tooltip>
                                </template>
                                <component
                                    :is="`task-${getType(schema, key)}`"
                                    :model-value="modelValue?.[key]"
                                    :task="modelValue"
                                    @update:model-value="onObjectInput(key, $event)"
                                    :root="getKey(key)"
                                    :schema="schema"
                                    :required="isRequired(key)"
                                    :definitions="definitions"
                                    class="mt-1 mb-2 wrapper"
                                    v-bind="getType(schema, key) === 'complex' ? {metadataInputs} : {}"
                                />
                            </el-form-item>
                        </template>
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
    import Information from "vue-material-design-icons/InformationOutline.vue";
    import Help from "vue-material-design-icons/HelpBox.vue";
    import Kicon from "../../Kicon.vue";
    import Editor from "../../inputs/Editor.vue";
    import Markdown from "../../layout/Markdown.vue";
    import TaskDict from "./TaskDict.vue";
    import TaskWrapper from "./TaskWrapper.vue";

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
            Information,
            Help,
            Kicon,
            Editor,
            Markdown,
            TaskWrapper,
        },
        props: {
            properties: {
                type: Object,
                default: () => ({}),
            },
            expandOptional: {type: Boolean, default: false},
            metadataInputs: {type: Boolean, default: false}
        },
        emits: ["update:modelValue"],
        data() {
            return {
                activeNames: [],
            };
        },
        mounted() {
            if (this.expandOptional) {
                this.activeNames = ["optional"];
            }
        },
        computed: {
            sortedProperties() {
                return sortProperties(this.properties, this.schema?.required);
            },
            requiredProperties() {
                return this.sortedProperties.filter(([p,v]) => v && this.isRequired(p));
            },
            optionalProperties() {
                return this.sortedProperties.filter(([p,v]) => v && !this.isRequired(p) && !v.$deprecated);
            },
            deprecatedProperties() {
                return this.sortedProperties.filter(([_,v]) => v && v.$deprecated);
            }
        },
        methods: {
            onObjectInput(propertyName, value) {
                const currentValue = this.modelValue || {};
                currentValue[propertyName] = value;
                this.onInput(currentValue);
            },
            isValidated(key) {
                return (
                    this.isRequired(key) &&
                    !this.modelValue?.[key] &&
                    this.schema.properties[key].default === undefined
                );
            },
            hasTooltip(schema) {
                return schema.title || schema.description;
            },
            helpText(schema) {
                return (
                    (schema.title ? "**" + schema.title + "**" : "") +
                    (schema.title && schema.description ? "\n" : "") +
                    (schema.description ? schema.description : "")
                );
            },
            isAnyOf(schema) {
                return !!schema?.anyOf;
            }
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
</style>

<style lang="scss" scoped>
@import "../../code/styles/code.scss";

.type-tag {
    background-color: var(--ks-tag-background);
    color: var(--ks-tag-content);
}

.el-form-item.is-required:not(.is-no-asterisk).asterisk-left {
    > :deep(.el-form-item__label) {
        display: flex;
    }
}

.label {
    color: var(--ks-content-primary);
}

.el-tooltip__trigger {
    > :deep(svg) {
        fill: var(--ks-content-tertiary);
    }
}

.el-form-item {
    > :deep(.el-form-item__label) {
        align-items: center;
        justify-content: flex-start;
    }
}
</style>