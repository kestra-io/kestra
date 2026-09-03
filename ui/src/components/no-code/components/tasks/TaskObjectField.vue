<template>
    <component
        v-if="simpleType === 'list'"
        ref="taskComponent"
        :is="type"
        v-bind="componentProps"
        :disabled
        class="mt-1 mb-2 wrapper"
    />
    <component
        v-else-if="frameRoot"
        ref="taskComponent"
        :is="type"
        v-bind="componentProps"
        :bare="true"
        :disabled
        class="wrapper"
    />
    <div v-else-if="isNestedObject" class="nested-card">
        <div class="nested-card-head">
            <span class="nested-card-label">{{ fieldKey }}</span>
            <ClearButton
                v-if="isAnyOf && !isRequired && hasSelectedASchema"
                @click="modelValue = undefined; taskComponent?.resetSelectType?.();"
            />
            <span v-if="!isAnyOf" class="type-pill">{{ simpleType }}</span>
            <KsTooltip
                v-if="hasTooltip && !inlineHelp"
                placement="left-start"
                :showArrow="false"
                popperClass="singleton-tooltip"
            >
                <template #content>
                    <KsMarkdown class="markdown-tooltip" :content="helpText" />
                </template>
                <Help />
            </KsTooltip>
        </div>
        <div class="nested-card-body">
            <component
                ref="taskComponent"
                :is="type"
                v-bind="componentProps"
                :bare="true"
                :disabled
            />
            <KsMarkdown
                v-if="inlineHelp && inlineHelpText"
                class="field-help"
                :content="inlineHelpText"
            />
        </div>
    </div>
    <KsFormItem v-else-if="fieldKey" :required="isRequired" for="" :data-test="`field-${fieldKey}`">
        <template #label>
            <div class="inline-wrapper">
                <div class="inline-start">
                    <span v-if="props.fieldKey" class="label">
                        {{ props.fieldKey }}
                    </span>

                    <span
                        v-if="defaultHint !== undefined"
                        class="plugin-default-hint"
                        data-test="field-default-hint"
                        :title="$t(pluginDefault !== undefined ? 'block_editor.plugin_default_tooltip' : 'block_editor.schema_default_tooltip')"
                    >
                        {{ $t("block_editor.plugin_default", {value: defaultHint}) }}
                    </span>

                    <ClearButton
                        v-if="isAnyOf && !isRequired && hasSelectedASchema"
                        @click="modelValue = undefined; taskComponent?.resetSelectType?.();"
                    />
                </div>
                <span v-if="!isAnyOf" class="type-pill">{{ simpleType }}</span>
                <KsTooltip
                    v-if="!isAnyOf && hasTooltip && !inlineHelp"
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
                <TaskLabelWithBoolean
                    class="inline-boolean"
                    :type="simpleType"
                    :isBoolean="isBoolean"
                    :componentProps="componentProps"
                />
                <component
                    v-if="isNumber"
                    ref="taskComponent"
                    :is="type"
                    v-bind="componentProps"
                    :disabled
                    class="inline-number"
                />
                <KsIconButton
                    v-if="hasToggle"
                    filled
                    class="inline-code-toggle"
                    :type="pebbleState ? 'primary' : 'default'"
                    :tooltip="$t('no_code.toggle_pebble')"
                    :aria-label="$t('no_code.toggle_pebble')"
                    @click="pebbleState = !pebbleState"
                >
                    <IconCodeTags />
                </KsIconButton>
            </div>
        </template>
        <component
            v-if="!isBoolean && !isNumber"
            ref="taskComponent"
            :is="type"
            v-bind="componentProps"
            :disabled
            class="mt-1 mb-2 wrapper"
        />
        <KsMarkdown
            v-if="inlineHelp && inlineHelpText && !isBoolean && !isNumber"
            class="field-help"
            :content="inlineHelpText"
        />
        <span
            v-if="isMissingRequired"
            class="required-missing"
            data-test="field-required-missing"
        >
            <AlertCircleOutline class="required-missing-icon" />
            {{ $t("block_editor.required_missing") }}
        </span>
    </KsFormItem>
</template>

<script setup lang="ts">
    import {computed, inject, ref, useTemplateRef} from "vue"
    import {useBlockComponent} from "./useBlockComponent"
    import {FIELD_NAV_INJECTION_KEY, PLUGIN_DEFAULTS_INJECTION_KEY} from "../../injectionKeys"

    import ClearButton from "./ClearButton.vue"
    import {KsMarkdown} from "@kestra-io/design-system"
    import Help from "vue-material-design-icons/Information.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import IconCodeTags from "vue-material-design-icons/CodeTags.vue"
    import TaskLabelWithBoolean from "./TaskLabelWithBoolean.vue"

    const modelValue = defineModel<any>()

    const props = withDefaults(defineProps<{
        schema: any;
        root?: string;
        fieldKey: string;
        task: any;
        required?: string[];
        disabled?: boolean;
        siblingKeys?: string[];
        rootOverride?: string;
        frameRoot?: boolean;
    }>(), {frameRoot: false})

    const taskComponent = useTemplateRef<{resetSelectType?: () => void}>("taskComponent")

    const isRequired = computed(() => {
        return !props.disabled && props.required?.includes(props.fieldKey)// && props.schema.$required;
    })

    const isMissingRequired = computed(() => {
        if (!isRequired.value) return false
        const value = modelValue.value
        return value === undefined || value === null || value === "" || (Array.isArray(value) && value.length === 0)
    })

    const hasSelectedASchema = ref(false)

    const pebbleState = ref(false)

    const componentProps = computed(() => {
        return {
            modelValue: modelValue.value,
            "onUpdate:modelValue": (value: Record<string, any> | string | number | boolean | Array<any>) => {
                modelValue.value = value
            },
            "onUpdate:selectedSchema": (value: any) => {
                hasSelectedASchema.value = value !== undefined
            },
            pebble: pebbleState.value,
            "onUpdate:pebble": (value: boolean) => {
                pebbleState.value = value
            },
            task: props.task,
            root: props.rootOverride ?? (props.root ? `${props.root}.${props.fieldKey}` : props.fieldKey),
            schema: props.schema,
            required: isRequired.value,
        }
    })

    const hasTooltip = computed(() => {
        return props.schema?.title || props.schema?.description || props.schema?.markdownDescription
    })

    const helpText = computed(() => {
        const schema = props.schema
        if (!schema) return ""

        const description = schema.description || schema.markdownDescription
        return (
            (schema.title ? "**" + schema.title + "**" : "") +
            (schema.title && description ? "\n" : "") +
            (description ? description : "")
        )
    })

    const isAnyOf = computed(() => {
        return Boolean(props.schema?.anyOf)
    })

    const isBoolean = computed(() => {
        if (simpleType.value === "boolean") return true
        const anyOf = props.schema?.anyOf
        if (!Array.isArray(anyOf) || anyOf.length !== 2) return false
        return anyOf.some(s => s.type === "boolean") && anyOf.some(s => s.type === "string" && !s.format)
    })

    const simpleType = computed(() => {
        return type.value.ksTaskName
    })

    const isNumber = computed(() => simpleType.value === "number")

    const hasToggle = computed(() =>
        ["duration", "date-time"].includes(props.schema?.format ?? ""),
    )

    const {getBlockComponent} = useBlockComponent()

    const type = computed(() => {
        return getBlockComponent.value(props.schema ?? {}, props.fieldKey, props.siblingKeys)
    })


    const pluginDefaults = inject(PLUGIN_DEFAULTS_INJECTION_KEY, undefined)
    const pluginDefault = computed(() => {
        const value = pluginDefaults?.value?.[props.fieldKey]
        return value === undefined || value === null || typeof value === "object" ? undefined : String(value)
    })

    const schemaDefault = computed(() => {
        const value = props.schema?.default
        return value === undefined || value === null || typeof value === "object" ? undefined : String(value)
    })

    // The flow's pluginDefaults override the schema default at runtime, so they win the hint too.
    const defaultHint = computed(() => pluginDefault.value ?? schemaDefault.value)

    const fieldNav = inject(FIELD_NAV_INJECTION_KEY, undefined)

    const inlineHelp = computed(() => Boolean(fieldNav))
    const inlineHelpText = computed(() => props.schema?.description || props.schema?.markdownDescription || props.schema?.title || "")

    const isObjectAnyOf = computed(() => {
        const anyOf = props.schema?.anyOf
        if (!Array.isArray(anyOf) || anyOf.length === 0) return false
        return anyOf.every((s: any) => s.$ref || s.allOf || s.type === "object")
    })

    const isNestedObject = computed(() =>
        Boolean(props.fieldKey)
        && (simpleType.value === "complex" || simpleType.value === "object"
            || (simpleType.value === "any-of" && isObjectAnyOf.value)),
    )
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

.required-missing {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    margin-top: var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-error);
}

.required-missing-icon {
    display: inline-flex;
    font-size: var(--ks-font-size-sm);
}

.field-help {
    margin-top: var(--ks-spacing-1);
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-muted);
    line-height: 1.45;
    text-wrap: pretty;
}

.inline-wrapper {
    width: 100%;
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    min-width: 0;

    .inline-start {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
        flex: 0 1 auto;
    }

    .label {
        color: var(--ks-text-primary);
        min-width: 0;
        flex: 0 1 auto;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
    }

    .plugin-default-hint {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-muted);
    }

    .information-icon {
        color: var(--ks-text-secondary);
        cursor: pointer;
    }
}

.inline-boolean {
    margin-left: auto;
    flex-shrink: 0;
}

.inline-number {
    margin-left: auto;
    flex-shrink: 0;
    width: 8rem;
}

.inline-code-toggle {
    margin-left: auto;
    flex-shrink: 0;
}

.type-pill {
    flex-shrink: 0;
    font-size: var(--ks-font-size-xs);
    line-height: 1.5;
    padding: 0 var(--ks-spacing-2);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-tag-inactive);
    border: 1px solid var(--ks-border-subtle);
    color: var(--ks-text-secondary);
    text-transform: capitalize;
}

.nested-card {
    border: 1px solid var(--ks-border-subtle);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-surface);
    overflow: hidden;
    margin: var(--ks-spacing-1) 0 var(--ks-spacing-2);
}

.nested-card-head {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    background: var(--ks-bg-elevated);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.nested-card-label {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    color: var(--ks-text-primary);
}

.nested-card-body {
    padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
}
</style>
