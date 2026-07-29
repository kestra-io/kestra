<template>
    <component
        v-if="simpleType === 'list'"
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
        <component
            v-if="!isBoolean"
            ref="taskComponent"
            :is="type"
            v-bind="componentProps"
            :disabled
            class="mt-1 mb-2 wrapper"
        />
    </KsFormItem>
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef} from "vue"
    import {useBlockComponent} from "./useBlockComponent"

    import ClearButton from "./ClearButton.vue"
    import {KsMarkdown} from "@kestra-io/design-system"
    import Help from "vue-material-design-icons/Information.vue"
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue"


    const modelValue = defineModel<any>()

    const props = defineProps<{
        schema: any;
        root?: string;
        fieldKey: string;
        task: any;
        required?: string[];
        disabled?: boolean;
        siblingKeys?: string[];
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
        return getBlockComponent.value(props.schema ?? {}, props.fieldKey, props.siblingKeys)
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
        background-color: var(--ks-bg-active);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-xs);
        padding: 0 var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        text-transform: capitalize;
    }

    .information-icon {
        color: var(--ks-text-secondary);
        cursor: pointer;
    }
}
</style>