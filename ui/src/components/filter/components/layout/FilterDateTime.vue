<template>
    <div class="p-3">
        <el-date-picker
            :modelValue="dateValue"
            type="datetime"
            :placeholder="`Select ${label.toLowerCase()}`"
            @update:model-value="$emit('update:dateValue', $event)"
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