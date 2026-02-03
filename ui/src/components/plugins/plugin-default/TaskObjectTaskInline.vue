<template>
    <TaskObjectInline
        v-model="modelValue"
        :parentPath
        :blockSchemaPath="taskSchemaPath"
    />
</template>

<script setup lang="ts">
    import TaskObjectInline from "./TaskObjectInline.vue";

    const modelValue = defineModel<Record<string, any>>();

    defineProps<{
        parentPath: string;
        taskSchemaPath: string;
    }>();
</script>
