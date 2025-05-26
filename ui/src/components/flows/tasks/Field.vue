<template>
    <el-form-item :required="isRequired(fieldKey)">
        <template #label>
            <div class="inline-wrapper">
                <div class="inline-start">
                    <TaskLabelWithBoolean
                        :type="getType(schema)"
                        :is-boolean="isBoolean(schema)"
                        :component-props="componentProps(fieldKey, schema)"
                    />
                    <span v-if="getKey(fieldKey)" class="label">
                        {{ getKey(fieldKey) }}
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
            v-model="model[fieldKey]"
            :is="`task-${getType(schema, fieldKey)}`"
            v-bind="{...componentProps(fieldKey, schema)}"
            class="mt-1 mb-2 wrapper"
        />
    </el-form-item>
</template>

<script setup lang="ts">
    import Help from "vue-material-design-icons/Information.vue";
    import Markdown from "../../layout/Markdown.vue";
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue";
    import {getType} from "./Task";

    const props = defineProps<{
        schema: any;
        definitions: any;
        fieldKey: string;
    }>()

    const model = defineModel<any>({
        type: Object,
        default: () => ({})
    });

    function isRequired(fieldKey: string) {
        return props.schema.required?.includes(fieldKey);
    }

    function componentProps(key: string, schema: any){
        return {
            task: model.value,
            root: getKey(key),
            schema: schema,
            required: isRequired(key),
            definitions: props.definitions
        }
    }

    function hasTooltip(schema:any) {
        return schema.title || schema.description;
    }
    function helpText(schema: any) {
        return (
            (schema.title ? "**" + schema.title + "**" : "") +
            (schema.title && schema.description ? "\n" : "") +
            (schema.description ? schema.description : "")
        );
    }
    function isAnyOf(schema: any) {
        return !!schema?.anyOf;
    }
    function isBoolean(schema: any) {
        return getType(schema) === "boolean";
    }
    function isNestedProperty(key: string) {
        return key.includes(".") ||
            ["interval", "maxInterval", "minInterval", "type"].includes(key);
    }
    function getKey(key: string) {
        if (isNestedProperty(key) || key === "id") {
            return key;
        }
        return key.charAt(0).toUpperCase() + key.slice(1);
    }
</script>