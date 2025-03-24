<template>
    <el-form label-position="top">
        <el-form-item
            :key="index"
            :required="isRequired(key)"
            v-for="(schema, key, index) in properties"
        >
            <template #label>
                <span v-if="required" class="me-1 text-danger">*</span>
                <span v-if="getKey(key)" class="label">
                    {{
                        getKey(key)
                            .split(".")
                            .map(
                                (word) =>
                                    word.charAt(0).toUpperCase() +
                                    word.slice(1),
                            )
                            .join(" ")
                    }}
                </span>
                <el-tag disable-transitions size="small" class="ms-2 type-tag">
                    {{ getType(schema, key) }}
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
                :is="`task-${getType(schema)}`"
                :model-value="getPropertiesValue(key)"
                @update:model-value="onObjectInput(key, $event)"
                :root="getKey(key)"
                :schema="schema"
                :required="isRequired(key)"
                :definitions="definitions"
                :min="getExclusiveMinimum(key)"
            />
        </el-form-item>
    </el-form>
</template>

<script>
    import Task from "./Task";
    import Information from "vue-material-design-icons/InformationOutline.vue";
    import Help from "vue-material-design-icons/HelpBox.vue";
    import Kicon from "../../Kicon.vue";
    import Editor from "../../inputs/Editor.vue";
    import Markdown from "../../layout/Markdown.vue";

    export default {
        name: "TaskBasic",
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
                    const properties = this.schema.properties;
                    return this.sortProperties(properties);
                }
                return undefined;
            },
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
                    .reduce((result, entry) => {
                        result[entry[0]] = entry[1];
                        return result;
                    }, {});
            },
            onObjectInput(properties, value) {
                const currentValue = this.modelValue || {};
                currentValue[properties] = value;
                this.$emit("update:modelValue", currentValue);
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
            getExclusiveMinimum(key) {
                const property = this.schema.properties[key];
                const propertyHasExclusiveMinimum =
                    property && property.exclusiveMinimum;
                return propertyHasExclusiveMinimum
                    ? property.exclusiveMinimum
                    : null;
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
</style>
