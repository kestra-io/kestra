<template>
    <el-form label-position="top">
        <template v-if="sortedProperties">
            <!-- Required properties -->
            <el-form-item
                :key="key"
                :required="isRequired(key)"
                v-for="[key, schema] in requiredProperties"
            >
                <template #label>
                    <span v-if="getKey(key)" class="label">
                        {{ getKey(key) }}
                    </span>
                    <el-tag
                        disable-transitions
                        size="small"
                        class="ms-2 type-tag"
                    >
                        {{ getType(schema) }}
                    </el-tag>
                    <el-tooltip
                        v-if="hasTooltip(schema)"
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
                />
            </el-form-item>

            <!-- Non required properties shown collapsed-->
            <el-collapse v-if="optionalProperties?.length" class="collapse">
                <el-collapse-item :title="$t('no_code.sections.optional')">
                    <el-form-item
                        :key="key"
                        :required="isRequired(key)"
                        v-for="[key, schema] in optionalProperties"
                    >
                        <template #label>
                            <span v-if="getKey(key)" class="label">
                                {{ getKey(key) }}
                            </span>
                            <el-tag
                                disable-transitions
                                size="small"
                                class="ms-2 type-tag"
                            >
                                {{ getType(schema) }}
                            </el-tag>
                            <el-tooltip
                                v-if="hasTooltip(schema)"
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
                        />
                    </el-form-item>
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

    export default {
        name: "TaskObject",
        mixins: [Task],
        components: {
            TaskDict,
            Information,
            Help,
            Kicon,
            Editor,
            Markdown,
        },
        emits: ["update:modelValue"],
        computed: {
            sortedProperties() {
                if (this.schema?.properties) {
                    return this.sortProperties(this.schema.properties);
                }

                return undefined;
            },
            requiredProperties() {
                const properties = this.sortedProperties.filter(([p]) => this.isRequired(p));
                // if the field id is not found in the required fields,
                // we need to add it
                if(!properties.find(([field]) => field === "id")) {
                    properties.unshift(["id", {type: "string", $required: true}]);
                }
                return properties;
            },
            optionalProperties() {
                return this.sortedProperties.filter(([p]) => !this.isRequired(p));
            },
        },
        methods: {
            sortProperties(properties) {
                if (!properties) {
                    return properties;
                }

                return Object.entries(properties)
                    .sort((a, b) => {
                        if (a[0] === "id") {
                            return -1;
                        } else if (b[0] === "id") {
                            return 1;
                        }

                        const aRequired = (this.schema.required || []).includes(
                            a[0],
                        );
                        const bRequired = (this.schema.required || []).includes(
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
            },
            onObjectInput(properties, value) {
                const currentValue = this.modelValue || {};
                currentValue[properties] = value;
                this.$emit("update:modelValue", currentValue);
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
        },
    };
</script>

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
