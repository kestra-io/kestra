<template>
    <div class="p-3">
        <KsInput
            :modelValue="textValue"
            :placeholder="`Enter ${label.toLowerCase()}`"
            @update:model-value="emits('update:textValue', String($event))"
        />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        label: string;
        textValue: string;
    }>()

    const emits = defineEmits<{
        "update:textValue": [value: string];
    }>()
</script>
