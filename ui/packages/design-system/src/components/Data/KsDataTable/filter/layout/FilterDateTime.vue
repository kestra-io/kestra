<template>
    <div class="filter-datetime-container">
        <KsDatePicker
            :modelValue="dateValue"
            type="datetime"
            :placeholder="`Select ${label.toLowerCase()}`"
            @update:model-value="$emit('update:dateValue', $event ?? null)"
        />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        label: string;
        dateValue: Date | null;
    }>()

    defineEmits<{
        "update:dateValue": [value: Date | null];
    }>()
</script>

<style scoped lang="scss">
    .filter-datetime-container {
        padding: var(--ks-spacing-4);
    }
</style>