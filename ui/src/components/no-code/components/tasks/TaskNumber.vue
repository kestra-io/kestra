<template>
    <KsInputNumber
        :modelValue="val"
        @update:model-value="onInput"
        :state="isValid"
        :min="schema?.minimum as number | undefined"
        :max="schema?.maximum as number | undefined"
        :step="schema?.step as number | undefined"
        type="number"
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"

    const props = withDefaults(defineProps<{
        modelValue?: object | string | number | boolean | unknown[]
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>(), {
        modelValue: undefined,
        schema: undefined,
        required: false,
        task: undefined,
        root: undefined,
        definitions: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: unknown]
    }>()

    const values = computed(() => props.modelValue ?? (props.schema as Record<string, unknown> | undefined)?.default)

    const isValid = computed(() => {
        if (props.required && props.modelValue === undefined) {
            return false
        }

        if (props.modelValue !== undefined) {
            return !isNaN(props.modelValue as number)
        }

        return true
    })

    const val = computed(() => {
        return values.value ? parseInt(values.value.toString(), 10) : undefined
    })

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>
