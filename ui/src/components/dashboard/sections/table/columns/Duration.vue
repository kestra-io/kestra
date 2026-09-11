<template>
    <span v-if="field">{{ durationUtils.humanDuration(calculatedField) }}</span>
    <em v-else>{{ durationUtils.humanDuration(calculatedField) }}</em>
</template>

<script setup lang="ts">
    import {durationUtils} from "@kestra-io/design-system"
    import {computeDurationBreakdown, type DurationHistoryEntry} from "@kestra-io/topology"
    import {computed} from "vue"

    const props = defineProps<{
        field?: number | string | null,
        histories?: DurationHistoryEntry[],
        queued?: boolean,
        startDate?: string
    }>()

    // handle case where execution is non-terminated, then there is no duration, we calculate it live to display it to the user
    const liveField = computed(() => {
        if (props.histories) {
            const breakdown = computeDurationBreakdown(props.histories)
            return (props.queued ? breakdown.queued : breakdown.duration) / 1000
        }
        return props.startDate ? (+new Date() - new Date(props.startDate).getTime()) / 1000 : 0
    })

    const calculatedField = computed(() => props.field ?? liveField.value)
</script>
