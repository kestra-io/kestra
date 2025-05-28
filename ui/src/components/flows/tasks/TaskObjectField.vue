<template>
    <el-form-item v-if="fieldKey" :required="isRequired(fieldKey)">
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
                        <Markdown
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
            :is="`task-${getType(schema, fieldKey, props.definitions)}`"
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
        task: any;
        modelValue?: Record<string, any> | string | number | boolean | Array<any>,
        required?: string[];
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value: Record<string, any> | string | number | boolean | Array<any>): void;
    }>();

    function isRequired(fieldKey: string) {
        return props.required?.includes(fieldKey);
    }

    function componentProps(key: string, schema: any){
        return {
            modelValue: props.modelValue,
            "onUpdate:modelValue": (value: Record<string, any> | string | number | boolean | Array<any>) => {
                emit("update:modelValue", value);
            },
            task: props.task,
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

<style lang="scss" scoped>
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