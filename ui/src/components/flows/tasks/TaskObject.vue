<template>
    <template v-if="properties">
        <el-form-item
            :key="index"
            :required="isRequired(key)"
            v-for="(schema, key, index) in properties"
        >
            <template #label>
                <span class="d-flex flex-grow-1">
                    <span class="me-auto">
                        <code>{{ getKey(key) }}</code>&nbsp;
                        <el-tooltip v-if="hasTooltip(schema)" :persistent="false" transition="" :hide-after="0">
                            <template #content>
                                <markdown class="markdown-tooltip" :source="helpText(schema)" />
                            </template>
                            <help />
                        </el-tooltip>
                    </span>
                    <span>
                        <el-tag disable-transitions type="info" size="small">
                            {{ getType(schema) }}
                        </el-tag>
                    </span>
                </span>
            </template>
            <component
                :is="`task-${getType(schema, key)}`"
                :model-value="getPropertiesValue(key)"
                :task="modelValue"
                @update:model-value="onObjectInput(key, $event)"
                :root="getKey(key)"
                :schema="schema"
                :required="isRequired(key)"
                :definitions="definitions"
            />
        </el-form-item>
    </template>
    <template v-else>
        <task-dynamic
            :model-value="editorValue"
            :root="root"
            :schema="schema"
            :definitions="definitions"
            @update:model-value="onInput"
        />
    </template>
</template>

<script>
    import Task from "./Task";
    import Information from "vue-material-design-icons/InformationOutline.vue";
    import Help from "vue-material-design-icons/HelpBox.vue";
    import Kicon from "../../Kicon.vue";
    import Editor from "../../inputs/Editor.vue";
    import Markdown from "../../layout/Markdown.vue";

    export default {
        name: "TaskObject",
        mixins: [Task],
        components: {
            Information,
            Help,
            Kicon,
            Editor,
            Markdown,
        },
        emits: ["update:modelValue"],
        computed: {
            properties() {
                if (this.schema) {
                    const properties = this.schema.properties
                    return this.sortProperties(properties)
                }

                return undefined;
            }
        },
        methods: {
            getPropertiesValue(properties) {
                return this.modelValue && this.modelValue[properties]
                    ? this.modelValue[properties]
                    : undefined;
            },
            sortProperties(properties) {
                if (!properties) {
                    return properties;
                }

                return Object
                    .entries(properties)
                    .sort((a, b) => {
                        if (a[0] === "id") {
                            return -1;
                        } else if (b[0] === "id") {
                            return 1;
                        }

                        const aRequired = (this.schema.required || []).includes(a[0]);
                        const bRequired = (this.schema.required || []).includes(b[0]);

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
                    .reduce((result, entry) => {
                        result[entry[0]] = entry[1];
                        return result
                    }, {});
            },
            onObjectInput(properties, value) {
                const currentValue = this.modelValue || {};
                currentValue[properties] = value;
                this.$emit("update:modelValue", currentValue);
            },
            isValidated(key) {
                return (
                    this.isRequired(key) &&
                    !this.getPropertiesValue(key) &&
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
    .el-form-item.is-required:not(.is-no-asterisk).asterisk-left {
         > :deep(.el-form-item__label) {
             display: flex;

        }
    }

</style>
