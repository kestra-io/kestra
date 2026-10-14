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
    import dayjs from "../../date/dayjs"
    import {dateFilter} from "../../utils/date"

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

    const from = computed(() => props.date ? dayjs(props.date).fromNow() : "")

    const full = computed(() => props.date ? dateFilter(props.date, props.format) : "")
</script>
