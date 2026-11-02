<template>
    <KsInputNumber
        :modelValue="val"
        @update:model-value="onInput"
        :state="isValid"
        :min="schema?.minimum as number | undefined"
        :max="schema?.maximum as number | undefined"
        :step="schema?.step as number | undefined"
        type="number"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"

    type ModelValue = object | string | number | boolean | unknown[]

    const props = withDefaults(defineProps<{
        modelValue?: ModelValue
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
        "update:modelValue": [value: ModelValue | undefined]
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

    // Number() keeps decimals (parseInt turned 2.5 into 2) and the explicit empty check keeps 0;
    // anything that is not a finite number (a Pebble expression, for example) stays out of the box.
    const val = computed(() => {
        if (values.value === undefined || values.value === null || values.value === "") {
            return undefined
        }
        const parsed = Number(values.value)
        return Number.isFinite(parsed) ? parsed : undefined
    })

    function onInput(value: number | undefined) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>
