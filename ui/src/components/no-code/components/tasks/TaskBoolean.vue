<template>
    <KsSwitch
        :modelValue="modelValue"
        :aria-label="fieldName"
        @update:model-value="onInput"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"

    const props = defineProps<{modelValue?: boolean, root?: string}>()

    const emit = defineEmits<{(e: "update:modelValue", value: boolean): void}>()

    const fieldName = computed(() => props.root?.split(".").pop()?.replace(/\[\d+\]$/, "") || undefined)

    const onInput = (value: string | number | boolean | undefined) => emit("update:modelValue", Boolean(value))
</script>
