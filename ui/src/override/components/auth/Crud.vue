<template>
    <span />
</template>
<script lang="ts" setup>
    defineProps({
        permission: {
            type: String,
            required: true
        },
        type: {
            type: String,
            default: undefined
        },
        detail: {
            type: Object,
            required: true
        },
    });
</script>
