<template>
    <el-date-picker
        v-model="dateModel"
        type="datetime"
        :placeholder="`Select ${label.toLowerCase()}`"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps<{
        label: string;
        dateValue: Date | null;
    }>();

    const emits = defineEmits<{
        "update:dateValue": [value: Date | null];
    }>();

    const dateModel = computed({
        get: () => props.dateValue,
        set: (value: Date | null) => emits("update:dateValue", value)
    });
</script>