<template>
    <TaskObjectListInline
        v-if="inlineMode && simpleType === 'list'"
        v-model="modelValue"
        :fieldKey
        :root="componentProps.root"
        :taskSchemaPath
    />

    <component
        v-else-if="simpleType === 'list'"
        ref="taskComponent"
        :is="type"
        v-bind="componentProps"
        :disabled
        class="mt-1 mb-2 wrapper"
    />
    <KsFormItem v-else-if="fieldKey" :required="isRequired">
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
                        @click="modelValue = undefined; taskComponent?.resetSelectType?.();"
                    />
                </div>
                <KsTag
                    v-if="!isAnyOf"
                    disableTransitions
                    size="small"
                    class="type-tag"
                >
                    {{ simpleType }}
                </KsTag>
                <KsTooltip
                    v-if="!isAnyOf && hasTooltip"
                    placement="left-start"
                    :showArrow="false"
                    popperClass="singleton-tooltip"
                >
                    <template #content>
                        <KsMarkdown
                            class="markdown-tooltip"
                            :content="helpText"
                        />
                    </template>
                    <Help />
                </KsTooltip>
            </div>
        </template>
        <TaskObjectTaskInline
            v-if="inlineMode && simpleType === 'task'"
            v-model="modelValue"
            :parentPath="componentProps.root"
            :taskSchemaPath
        />
        <component
            v-else-if="!isBoolean"
            ref="taskComponent"
            :is="type"
            v-bind="componentProps"
            :disabled
            class="mt-1 mb-2 wrapper"
        />
    </KsFormItem>
</template>

<script setup lang="ts">
    import {computed, inject, ref, useTemplateRef} from "vue"
    import {useBlockComponent} from "./useBlockComponent"
    import {INLINE_TASK_MODE_INJECTION_KEY, BLOCK_SCHEMA_PATH_INJECTION_KEY} from "../../injectionKeys"

    import ClearButton from "./ClearButton.vue"
    import {KsMarkdown} from "@kestra-io/design-system"
    import Help from "vue-material-design-icons/Information.vue"
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue"
    import TaskObjectListInline from "../../../plugins/plugin-default/TaskObjectListInline.vue"
    import TaskObjectTaskInline from "../../../plugins/plugin-default/TaskObjectTaskInline.vue"


    const modelValue = defineModel<any>()

    const props = defineProps<{
        schema: any;
        root?: string;
        fieldKey: string;
        task: any;
        required?: string[];
        disabled?: boolean;
    }>()

    const taskComponent = useTemplateRef<{resetSelectType?: () => void}>("taskComponent")

    const isRequired = computed(() => {
        return !props.disabled && props.required?.includes(props.fieldKey)// && props.schema.$required;
    })

    const hasSelectedASchema = ref(false)


    const componentProps = computed(() => {
        return {
            modelValue: modelValue.value,
            "onUpdate:modelValue": (value: Record<string, any> | string | number | boolean | Array<any>) => {
                modelValue.value = value
            },
            "onUpdate:selectedSchema": (value: any) => {
                hasSelectedASchema.value = value !== undefined
            },
            task: props.task,
            root: props.root ? `${props.root}.${props.fieldKey}` : props.fieldKey,
            schema: props.schema,
            required: isRequired.value,
        }
    })

    const hasTooltip = computed(() => {
        return props.schema?.title || props.schema?.description
    })

    const helpText = computed(() => {
        const schema = props.schema
        if (!schema) return ""

        return (
            (schema.title ? "**" + schema.title + "**" : "") +
            (schema.title && schema.description ? "\n" : "") +
            (schema.description ? schema.description : "")
        )
    })

    const isAnyOf = computed(() => {
        return Boolean(props.schema?.anyOf)
    })

    const isBoolean = computed(() => {
        return type.value === "boolean"
    })

    const simpleType = computed(() => {
        return type.value.ksTaskName
    })

    const {getBlockComponent} = useBlockComponent()

    const type = computed(() => {
        return getBlockComponent.value(props.schema ?? {}, props.fieldKey)
    })

    /** Whether the component is rendered in inline mode (used for Plugin Defaults) */
    const inlineMode = inject(INLINE_TASK_MODE_INJECTION_KEY, false)
    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))

    /**
     * Resolves the JSON schema path for the current field.
     * Used by inline components to fetch metadata for nested objects or list items.
     */
    const taskSchemaPath = computed(() => {
        if (props.schema?.items?.$ref) {
            return props.schema.items.$ref
        }

        if (props.schema?.$ref) {
            return props.schema.$ref
        }

        const itemsSuffix = simpleType.value === "list" ? ["items"] : []
        return [blockSchemaPathInjected.value, "properties", props.fieldKey, ...itemsSuffix].join("/")
    })
</script>

<style scoped lang="scss">
.kel-form-item {
    width: 100%;

    > :deep(.kel-form-item__label) {
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
        font-family: var(--kel-font-family-monospace);
        color: var(--ks-text-primary);
        min-width: 0;
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
    }

    .label-anyof{
        background-color: red;
    }

    .type-tag {
        background-color: var(--ks-bg-tag-active);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-xs);
        line-height: var(--ks-font-size-lg);
        padding: 0 8px;
        padding-bottom: 2px;
        border-radius: 8px;
        text-transform: capitalize;
    }

    .information-icon {
        color: var(--ks-text-secondary);
        cursor: pointer;
    }
}
</style>