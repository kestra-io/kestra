<template>
    <KsInputNumber
        :modelValue="val"
        @update:model-value="onInput"
        :state="isValid"
        :min="(schema?.minimum as number | undefined)"
        :max="(schema?.maximum as number | undefined)"
        :step="(schema?.step as number | undefined)"
        type="number"
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {collapseEmptyValues} from "./MixinTask"

    const props = defineProps<{
        modelValue?: unknown
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>()

    const emit = defineEmits<{
        "update:modelValue": [unknown]
    }>()

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }

    const values = computed(() =>
        props.modelValue !== undefined ? props.modelValue : props.schema?.default,
    )

    const isValid = computed<boolean>(() => {
        if (props.required && props.modelValue === undefined) {
            return false
        }

        if (props.modelValue !== undefined) {
            return !isNaN(props.modelValue as number)
        }

        return true
    })

    const val = computed<number | undefined>(() =>
        values.value !== undefined && values.value !== null
            ? parseInt(values.value!.toString(), 10)
            : undefined,
    )
</script>
