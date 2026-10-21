<template>
    <div class="filter-text">
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

<style scoped lang="scss">
    .filter-text {
        padding: var(--ks-spacing-4);
    }
</style>
