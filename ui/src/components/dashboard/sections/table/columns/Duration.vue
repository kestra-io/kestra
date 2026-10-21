<template>
    <span v-if="field">{{ Utils.humanDuration(calculatedField) }}</span>
    <em v-else>{{ Utils.humanDuration(calculatedField) }}</em>
</template>

<script setup lang="ts">
    import {Utils} from "@kestra-io/ui-libs";
    import {computed, ref, watch} from "vue";
    import {useIntervalFn} from "@vueuse/core";

    const props = defineProps<{
        field: number | undefined,
        startDate?: string
    }>();

    // A non-terminated execution carries no duration, so it is counted against a ticking clock:
    // a bare Date.now() stays cached in the computed and survives a refresh of identical props.
    const startedAt = computed(() => props.field === undefined && props.startDate
        ? new Date(props.startDate).getTime()
        : undefined);

    const now = ref(Date.now());
    const {pause, resume} = useIntervalFn(() => {
        now.value = Date.now();
    }, 1000, {immediate: false});

    watch(startedAt, (started) => (started === undefined ? pause() : resume()), {immediate: true});

    const calculatedField = computed(() => startedAt.value === undefined
        ? props.field ?? 0
        : (now.value - startedAt.value) / 1000);
</script>
