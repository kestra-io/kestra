<template>
    <div class="wrapper" :class="{'wrapper--toggle': hasToggle, 'wrapper--boolean': schema?.type === 'boolean'}">
        <KsDatePicker
            v-if="!pebble && schema?.format === 'date-time'"
            :modelValue="modelValue"
            type="date"
            :placeholder="$t('no_code.choose_placeholder', {field: root || 'date'})"
            @update:model-value="(v: Date | string | null) => onInput(v instanceof Date ? v.toISOString() : '')"
        />
        <TaskDuration
            v-if="!pebble && schema?.format === 'duration'"
            :modelValue="typeof modelValue === 'string' ? modelValue : undefined"
            class="duration-field"
            @update:model-value="onInput"
        />
        <TaskBoolean
            v-if="!pebble && schema?.type === 'boolean'"
            :modelValue="typeof modelValue === 'boolean' ? modelValue : undefined"
            class="boolean-field"
            @update:model-value="onInput"
        />
        <InputText
            v-if="disabled"
            :modelValue="String(modelValue ?? '')"
            readonly
            class="w-100 disabled-field"
        />
        <KsEditor
            v-else-if="pebble || (!schema?.format && schema?.type !== 'boolean')"
            class="string-editor"
            v-bind="editorBindings"
            :modelValue="editorValue"
            :navbar="false"
            :options="{fullHeight: false, largeSuggestions: false}"
            schemaType="flow"
            :lang="`${editorLanguage}-pebble`"
            :placeholder="placeholder"
            inline
            @update:model-value="onInput"
            style="z-index: 1;"
        />
    </div>
</template>

<style scoped lang="scss">
:deep(.kel-input__inner) {
    &::placeholder {
        color: var(--ks-text-inactive) !important;
    }
}
:deep(.placeholder) {
    top: -7px !important;
}

.wrapper {
    display: flex;
    align-items: stretch;
    justify-content: stretch;
    border-radius: var(--ks-radius-base);
    border: 1px solid var(--ks-border-default);
    overflow: hidden;
    width: 100%;
    transition: border-color 0.12s ease, box-shadow 0.12s ease;

    :deep(.disabled-field) {
        margin: 0!important;
        border-radius: 4px;
    }

    :deep(.ks-editor){
        flex: 1;
    }
}

.wrapper:not(.wrapper--toggle) :deep(.kel-input__wrapper),
.wrapper:not(.wrapper--toggle) :deep(.editor-container) {
    box-shadow: none;
}

.wrapper:not(.wrapper--toggle):focus-within {
    border-color: var(--ks-border-focus);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--ks-border-focus) 22%, transparent);
}

.wrapper--boolean {
    border: none;
    overflow: visible;
    justify-content: flex-start;
    align-items: center;
}

.wrapper--toggle {
    border: none;
    overflow: visible;
    align-items: flex-start;
    gap: var(--ks-spacing-2);
}

.wrapper--toggle > * {
    flex: 1;
    min-width: 0;
}

.wrapper--toggle > .string-editor {
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
    transition: border-color 0.12s ease, box-shadow 0.12s ease;
}

.wrapper--toggle > .string-editor:focus-within {
    border-color: var(--ks-border-focus);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--ks-border-focus) 22%, transparent);
}
</style>

<script lang="ts" setup>
    import {computed, onMounted, ref} from "vue"
    import $moment from "moment"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../../../composables/useEditorBindings"
    import InputText from "../inputs/InputText.vue"
    import TaskDuration from "./TaskDuration.vue"
    import TaskBoolean from "./TaskBoolean.vue"
    import {Schema} from "./getTaskComponent"

    defineOptions({inheritAttrs: false})

    const editorBindings = useEditorBindings()

    const props = defineProps<{
        disabled?: boolean;
        modelValue?: string | boolean;
        schema?: Schema;
        root?: string;
        task?: any;
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value: string | boolean | undefined): void;
    }>()

    const pebble = defineModel<boolean>("pebble", {default: false})

    const hasToggle = computed(() =>
        ["duration", "date-time"].includes(props.schema?.format ?? ""),
    )

    const editorLanguage = computed(() => {
        return props.schema?.$language ?? "plaintext"
    })

    const values = computed(() => {
        if (props.modelValue === undefined) {
            return props.schema?.default
        }

        return props.modelValue
    })

    onMounted(() => {
        const schema = props.schema
        if (!schema) return

        if (!["duration", "date-time"].includes(schema.format ?? "") || !props.modelValue) {
            pebble.value = false
        } else if (schema.format === "duration" && values.value) {
            pebble.value = !$moment.duration(props.modelValue as string).isValid()
        } else if (schema.format === "date-time" && values.value) {
            pebble.value = isNaN(Date.parse(props.modelValue as string))
        }
    })

    const buffer = ref<string>()

    function onInput(value: string | boolean | null | undefined) {
        if (typeof value === "string") {
            buffer.value = value
            emit("update:modelValue", value.trimEnd())
            return
        }

        buffer.value = undefined
        emit("update:modelValue", value ?? undefined)
    }

    const editorValue = computed(() => {
        if (typeof props.modelValue !== "string") return undefined

        return buffer.value?.trimEnd() === props.modelValue ? buffer.value : props.modelValue
    })

    const placeholder = computed(() => props.root?.split(".").pop() ?? "")

</script>
