<template>
    <el-form labelPosition="top">
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
                <el-tag disableTransitions size="small" class="ms-2 type-tag">
                    {{ getTaskComponent(schema, key, properties).ksTaskName }}
                </el-tag>
                <el-tooltip
                    v-if="hasTooltip(schema)"
                    :persistent="false"
                    :hideAfter="0"
                    effect="light"
                >
                    <template #content>
                        <Markdown
                            class="markdown-tooltip"
                            :source="helpText(schema)"
                        />
                    </template>
                    <Help class="ms-2" />
                </el-tooltip>
            </template>
            <component
                :is="getBlockComponent(schema, key, properties)"
                :modelValue="getPropertiesValue(key)"
                @update:model-value="onObjectInput(key, $event)"
                :root="getKey(key)"
                :schema="schema"
                :required="isRequired(key)"
                :min="getExclusiveMinimum(key)"
            />
        </el-form-item>
    </el-form>
</template>
<script setup>
    import Help from "vue-material-design-icons/HelpBox.vue";
    import Markdown from "../../../layout/Markdown.vue";
</script>
<script>
    import Task from "./MixinTask";
    import {useBlockComponent} from "./useBlockComponent";

    export default {
        name: "TaskBasic",
        mixins: [Task],
        emits: ["update:modelValue"],
        setup() {
            const {getBlockComponent} = useBlockComponent();
            return {
                getBlockComponent,
            };
        },
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

<style scoped lang="scss">
@import "../../styles/code.scss";

.type-tag {
    background-color: var(--ks-tag-background);
    color: var(--ks-tag-content);
}
</style>
