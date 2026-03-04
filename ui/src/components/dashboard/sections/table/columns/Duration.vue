<template>
    <span v-if="field">{{ Utils.humanDuration(calculatedField) }}</span>
    <em v-else>{{ Utils.humanDuration(calculatedField) }}</em>
</template>

<script setup lang="ts">
    import {Utils} from "@kestra-io/ui-libs";
    import {computed} from "vue";

    const props = defineProps<{
        field: number | undefined,
        startDate?: string
    }>();

    // handle case where execution is non-terminated, then there is no duration, we calculate it live to display it to the user
    const calculatedField = computed(() => props.field === undefined && props.startDate ? ((+new Date() - new Date(props.startDate).getTime()) / 1000 ) : props.field ?? 0);
</script>
