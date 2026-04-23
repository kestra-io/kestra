<template>
    <div class="p-3">
        <KsDatePicker
            :modelValue="dateValue"
            type="datetime"
            :placeholder="`Select ${label.toLowerCase()}`"
            @update:model-value="(v) => $emit('update:dateValue', (v ?? null) as Date | null)"
        />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        label: string;
        dateValue: Date | null;
    }>();

    defineEmits<{
        "update:dateValue": [value: Date | null];
    }>();
</script>