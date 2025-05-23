<template>
    <el-form label-position="top">
        <template v-if="sortedProperties">
            <template v-for="[key, schema] in requiredProperties" :key="key">
                <template v-if="key === 'id' || isNestedProperty(key)">
                    <el-form-item :required="isRequired(key)">
                        <template #label>
                            <div class="inline-wrapper">
                                <div class="inline-start">
                                    <TaskLabelWithBoolean
                                        :type="getType(schema)"
                                        :is-boolean="isBoolean(schema)"
                                        :component-props="componentProps(key, schema)"
                                    />
                                    <span v-if="getKey(key)" class="label">
                                        {{ getKey(key) }}
                                    </span>
                                </div>
                                <el-tag
                                    v-if="!isAnyOf(schema)"
                                    disable-transitions
                                    size="small"
                                    class="type-tag"
                                >
                                    {{ getType(schema) }}
                                </el-tag>
                                <el-tooltip
                                    v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                    :persistent="false"
                                    :hide-after="0"
                                    effect="light"
                                    placement="left-start"
                                    :show-arrow="false"
                                    popper-class="singleton-tooltip"
                                >
                                    <template #content>
                                        <markdown
                                            class="markdown-tooltip"
                                            :source="helpText(schema)"
                                        />
                                    </template>
                                    <help />
                                </el-tooltip>
                            </div>
                        </template>
                        <component
                            v-if="!isBoolean(schema)"
                            :is="`task-${getType(schema, key)}`"
                            v-bind="{
                                ...componentProps(key, schema),
                                ...(getType(schema, key) === 'complex' ? {metadataInputs} : {})
                            }"
                            class="mt-1 mb-2 wrapper"
                        />
                    </el-form-item>
                </template>
                
                <template v-else>
                    <TaskWrapper>
                        <template #tasks>
                            <el-form-item :required="isRequired(key)">
                                <template #label>
                                    <div class="inline-wrapper">
                                        <div class="inline-start">
                                            <TaskLabelWithBoolean
                                                :type="getType(schema)"
                                                :is-boolean="isBoolean(schema)"
                                                :component-props="componentProps(key, schema)"
                                            />
                                            <span v-if="getKey(key)" class="label">
                                                {{ getKey(key) }}
                                            </span>
                                        </div>
                                        <el-tag
                                            v-if="!isAnyOf(schema)"
                                            disable-transitions
                                            size="small"
                                            class="type-tag"
                                        >
                                            {{ getType(schema) }}
                                        </el-tag>
                                        <el-tooltip
                                            v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                            :persistent="false"
                                            :hide-after="0"
                                            effect="light"
                                            placement="left-start"
                                            :show-arrow="false"
                                            popper-class="singleton-tooltip"
                                        >
                                            <template #content>
                                                <markdown
                                                    class="markdown-tooltip"
                                                    :source="helpText(schema)"
                                                />
                                            </template>
                                            <help />
                                        </el-tooltip>
                                    </div>
                                </template>
                                <component
                                    v-if="!isBoolean(schema)"
                                    :is="`task-${getType(schema, key)}`"
                                    v-bind="{
                                        ...componentProps(key, schema),
                                        ...(getType(schema, key) === 'complex' ? {metadataInputs} : {})
                                    }"
                                    class="mt-1 mb-2 wrapper"
                                />
                            </el-form-item>
                        </template>
                    </TaskWrapper>
                </template>
            </template>

            <el-collapse v-model="activeNames" v-if="optionalProperties?.length" class="collapse">
                <el-collapse-item name="optional" :title="$t('no_code.sections.optional')">
                    <template v-for="[key, schema] in optionalProperties" :key="key">
                        <TaskWrapper>
                            <template #tasks>
                                <el-form-item :required="isRequired(key)">
                                    <template #label>
                                        <div class="inline-wrapper">
                                            <div class="inline-start">
                                                <TaskLabelWithBoolean
                                                    :type="getType(schema)"
                                                    :is-boolean="isBoolean(schema)"
                                                    :component-props="componentProps(key, schema)"
                                                />
                                                <span v-if="getKey(key)" class="label">
                                                    {{ getKey(key) }}
                                                </span>
                                            </div>
                                            <el-tag
                                                v-if="!isAnyOf(schema)"
                                                disable-transitions
                                                size="small"
                                                class="type-tag"
                                            >
                                                {{ getType(schema) }}
                                            </el-tag>
                                            <el-tooltip
                                                v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                                :persistent="false"
                                                :hide-after="0"
                                                effect="light"
                                                placement="left-start"
                                                :show-arrow="false"
                                                popper-class="singleton-tooltip"
                                            >
                                                <template #content>
                                                    <markdown
                                                        class="markdown-tooltip"
                                                        :source="helpText(schema)"
                                                    />
                                                </template>
                                                <help />
                                            </el-tooltip>
                                        </div>
                                    </template>
                                    <component
                                        :is="`task-${getType(schema, key)}`"
                                        v-bind="{
                                            ...componentProps(key, schema),
                                            ...(getType(schema, key) === 'complex' ? {metadataInputs} : {})
                                        }"
                                        v-if="!isBoolean(schema)"
                                        class="mt-1 mb-2 wrapper"
                                    />
                                </el-form-item>
                            </template>
                        </TaskWrapper>
                    </template>
                </el-collapse-item>
            </el-collapse>

            <el-collapse v-model="activeNames" v-if="deprecatedProperties?.length" class="collapse">
                <el-collapse-item name="deprecated" :title="$t('no_code.sections.deprecated')">
                    <template v-for="[key, schema] in deprecatedProperties" :key="key">
                        <TaskWrapper>
                            <template #tasks>
                                <el-form-item :required="isRequired(key)">
                                    <template #label>
                                        <div class="inline-wrapper">
                                            <div class="inline-start">
                                                <TaskLabelWithBoolean
                                                    :type="getType(schema)"
                                                    :is-boolean="isBoolean(schema)"
                                                    :component-props="componentProps(key, schema)"
                                                />
                                                <span v-if="getKey(key)" class="label">
                                                    {{ getKey(key) }}
                                                </span>
                                            </div>
                                            <el-tag
                                                v-if="!isAnyOf(schema)"
                                                disable-transitions
                                                size="small"
                                                class="type-tag"
                                            >
                                                {{ getType(schema) }}
                                            </el-tag>
                                            <el-tooltip
                                                v-if="!isAnyOf(schema) && hasTooltip(schema)"
                                                :persistent="false"
                                                :hide-after="0"
                                                effect="light"
                                                placement="left-start"
                                                :show-arrow="false"
                                                popper-class="singleton-tooltip"
                                            >
                                                <template #content>
                                                    <markdown
                                                        class="markdown-tooltip"
                                                        :source="helpText(schema)"
                                                    />
                                                </template>
                                                <help />
                                            </el-tooltip>
                                        </div>
                                    </template>
                                    <component
                                        :is="`task-${getType(schema, key)}`"
                                        v-bind="{
                                            ...componentProps(key, schema),
                                            ...(getType(schema, key) === 'complex' ? {metadataInputs} : {})
                                        }"
                                        v-if="!isBoolean(schema)"
                                        class="mt-1 mb-2 wrapper"
                                    />
                                </el-form-item>
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
    import Information from "vue-material-design-icons/InformationOutline.vue";
    import Help from "vue-material-design-icons/Information.vue";
    import Kicon from "../../Kicon.vue";
    import Editor from "../../inputs/Editor.vue";
    import Markdown from "../../layout/Markdown.vue";
    import TaskDict from "./TaskDict.vue";
    import TaskWrapper from "./TaskWrapper.vue";
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue";

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
            TaskLabelWithBoolean,
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
            },
            componentProps() {
                return (key, schema) => ({
                    modelValue: this.modelValue?.[key],
                    task: this.modelValue,
                    "onUpdate:modelValue": (event) => this.onObjectInput(key, event),
                    root: this.getKey(key),
                    schema: schema,
                    required: this.isRequired(key),
                    definitions: this.definitions
                })
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
            },
            isBoolean(schema) {
                return this.getType(schema) === "boolean";
            },
            isNestedProperty(key) {
                return key.includes(".") || 
                    ["interval", "maxInterval", "minInterval", "type"].includes(key);
            },
            getKey(key) {
                if (this.isNestedProperty(key) || key === "id") {
                    return key;
                }
                return key.charAt(0).toUpperCase() + key.slice(1);
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

.el-form-item {
    width: 100%;
    
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
    }

    .type-tag {
        background-color: var(--ks-tag-background-active);
        color: var(--ks-tag-content);
        font-size: 12px;
        line-height: 20px;
        padding: 0 8px;
        padding-bottom: 2px;
        border-radius: 8px;
    }
}
</style>