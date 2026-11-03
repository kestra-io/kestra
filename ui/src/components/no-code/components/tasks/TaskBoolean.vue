<template>
    <KsSwitch
        :modelValue="currentValue"
        :aria-label="fieldName"
        @update:model-value="onInput"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"

    const props = withDefaults(defineProps<{
        modelValue?: unknown
        root?: string
        schema?: {default?: boolean}
    }>(), {
        modelValue: undefined,
        root: undefined,
        schema: undefined,
    })

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const fieldName = computed(() => props.root?.split(".").pop()?.replace(/\[\d+\]$/, "") || undefined)

    const currentValue = computed(() => {
        if (props.modelValue === undefined) return props.schema?.default
        return typeof props.modelValue === "boolean" ? props.modelValue : undefined
    })

    const onInput = (value: string | number | boolean | undefined) => emit("update:modelValue", Boolean(value))
</script>
