<template>
    <TaskBoolean
        v-if="isBoolean"
        v-bind="componentProps"
    />
</template>

<script setup lang="ts">
    import TaskBoolean from "./TaskBoolean.vue";

    interface Props {
        type?: string
        isBoolean?: boolean
        componentProps?: Record<string, any>
    }

    withDefaults(defineProps<Props>(), {
        type: "",
        isBoolean: false,
        componentProps: () => ({})
    })
</script>
