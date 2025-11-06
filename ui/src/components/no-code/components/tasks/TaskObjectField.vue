<template>
    <component
        v-if="simpleType === 'list'"
        ref="taskComponent"
        :is="type"
        v-bind="componentProps"
        :disabled
        class="mt-1 mb-2 wrapper"
    />
    <el-form-item v-else-if="fieldKey" :required="isRequired">
        <template #label>
            <div class="inline-wrapper">
                <div class="inline-start">
                    <TaskLabelWithBoolean
                        :type="simpleType"
                        :isBoolean="isBoolean"
                        :componentProps="componentProps"
                    />
                    <span v-if="props.fieldKey" class="label">
                        {{ props.fieldKey }}
                    </span>

                    <ClearButton
                        v-if="isAnyOf && !isRequired && hasSelectedASchema"
                        @click="$emit('update:modelValue', undefined); taskComponent?.resetSelectType?.();"
                    />
                </div>
                <el-tag
                    v-if="!isAnyOf"
                    disableTransitions
                    size="small"
                    class="type-tag"
                >
                    {{ simpleType }}
                </el-tag>
                <el-tooltip
                    v-if="!isAnyOf && hasTooltip"
                    :persistent="false"
                    :hideAfter="0"
                    effect="light"
                    placement="left-start"
                    :showArrow="false"
                    popperClass="singleton-tooltip"
                >
                    <template #content>
                        <Markdown
                            class="markdown-tooltip"
                            :source="helpText"
                        />
                    </template>
                    <Help />
                </el-tooltip>
            </div>
        </template>
        <component
            v-if="!isBoolean"
            ref="taskComponent"
            :is="type"
            v-bind="componentProps"
            :disabled
            class="mt-1 mb-2 wrapper"
        />
    </el-form-item>
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef} from "vue";
    import Help from "vue-material-design-icons/Information.vue";
    import Markdown from "../../../layout/Markdown.vue";
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue";
    import ClearButton from "./ClearButton.vue";
    import {useBlockComponent} from "./useBlockComponent";
    

    const props = defineProps<{
        schema: any;
        root?: string;
        fieldKey: string;
        task: any;
        modelValue?: Record<string, any> | string | number | boolean | Array<any>,
        required?: string[];
        disabled?: boolean;
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value?: Record<string, any> | string | number | boolean | Array<any>): void;
    }>();

    const taskComponent = useTemplateRef<{resetSelectType?: () => void}>("taskComponent");

    const isRequired = computed(() => {
        return !props.disabled && props.required?.includes(props.fieldKey);// && props.schema.$required;
    })

    const hasSelectedASchema = ref(false)

    const componentProps = computed(() => {
        return {
            modelValue: props.modelValue,
            "onUpdate:modelValue": (value: Record<string, any> | string | number | boolean | Array<any>) => {
                emit("update:modelValue", value);
            },
            "onUpdate:selectedSchema": (value: any) => {
                hasSelectedASchema.value = value !== undefined;
            },
            task: props.task,
            root: props.root ? `${props.root}.${props.fieldKey}` : props.fieldKey,
            schema: props.schema,
            required: isRequired.value
        }
    })

    const hasTooltip = computed(() => {
        return props.schema.title || props.schema.description;
    })

    const helpText = computed(() => {
        const schema = props.schema;
        return (
            (schema.title ? "**" + schema.title + "**" : "") +
            (schema.title && schema.description ? "\n" : "") +
            (schema.description ? schema.description : "")
        );
    })

    const isAnyOf = computed(() => {
        return Boolean(props.schema?.anyOf);
    })

    const isBoolean = computed(() => {
        return type.value === "boolean";
    })

    const simpleType = computed(() => {
        return type.value.ksTaskName;
    })

    const {getBlockComponent} = useBlockComponent();

    const type = computed(() => {
        return getBlockComponent.value(props.schema, props.fieldKey)
    })
</script>

<style scoped lang="scss">
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
        font-family: var(--bs-font-monospace);
        color: var(--ks-content-primary);
        min-width: 0;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: 0.875rem;
    }

    .label-anyof{
        background-color: red;
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