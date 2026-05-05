<template>
    <KsTooltip
        v-if="showTooltip && date"
        :content="inverted ? from : full"
    >
        <span :class="className">{{ inverted ? full : from }}</span>
    </KsTooltip>
    <span v-else-if="date" :class="className">{{ inverted ? full : from }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import KsTooltip from "../Feedback/KsTooltip.vue"
    import {getMomentInstance, getDateFormatter} from "../../date"

    const props = withDefaults(defineProps<{
        date?: Date | string
        inverted?: boolean
        format?: string
        className?: string
        showTooltip?: boolean
    }>(), {
        date: undefined,
        inverted: false,
        format: undefined,
        className: undefined,
        showTooltip: true,
    })

    const from = computed(() => {
        const moment = getMomentInstance()
        if (!moment || !props.date) return ""
        return moment(props.date).fromNow()
    })

    const full = computed(() => {
        const formatter = getDateFormatter()
        if (!formatter || !props.date) return ""
        return formatter(props.date, props.format)
    })
</script>
