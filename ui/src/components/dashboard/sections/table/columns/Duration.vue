<template>
    <span>{{ Utils.humanDuration(props.field) }}</span>
</template>

<script setup lang="ts">
    import {Utils} from "@kestra-io/ui-libs";

    const props = defineProps({
        field: {
            type: Number,
            required: true,
        },
    });
</script>
