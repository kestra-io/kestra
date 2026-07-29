<template>
    <KsSwitch
        :modelValue="modelValue"
        @update:model-value="onInput"
    />
</template>

<script setup lang="ts">
    defineProps<{modelValue?: boolean}>()

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const onInput = (value: string | number | boolean | undefined) => emit("update:modelValue", Boolean(value))
</script>
