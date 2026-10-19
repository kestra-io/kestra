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
        modelValue?: boolean
        root?: string
        schema?: {default?: boolean}
    }>(), {
        modelValue: undefined,
        root: undefined,
        schema: undefined,
    })

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const fieldName = computed(() => props.root?.split(".").pop()?.replace(/\[\d+\]$/, "") || undefined)

    const currentValue = computed(() => props.modelValue ?? props.schema?.default)

    const onInput = (value: string | number | boolean | undefined) => emit("update:modelValue", Boolean(value))
</script>
