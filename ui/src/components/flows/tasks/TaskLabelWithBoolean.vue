<template>
    <component
        v-if="isBoolean"
        :is="`task-${type}`"
        v-bind="componentProps"
    />
</template>

<script setup lang="ts">
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
