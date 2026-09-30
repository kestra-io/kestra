<template>
    <KsTooltip v-if="date" :content="absolute">
        <span class="date">{{ date }}</span>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {date as dateFilter} from "../../../../../utils/filters"
    import {dateUtils, dayjs, KsTooltip} from "@kestra-io/design-system"

    const props = defineProps({
        field: {
            type: String,
            default: undefined,
        },
        relative: {
            type: Boolean,
            default: false,
        },
    })

    // The relative branch needs calendar(), which dateFilter cannot express, so it applies the
    // stored timezone itself rather than falling back to the machine's.
    const inTimezone = (value: string) => dayjs(value).tz(dateUtils.currentTimezone())

    const date = computed(() => {
        if (!props.field) return undefined
        // dayjs(undefined) returns the current date, which is not what an empty cell should show
        return props.relative
            ? inTimezone(props.field).calendar(null, {sameElse: "L [at] LT"})
            : dateFilter(props.field)
    })

    const absolute = computed(() =>
        props.field ? dateFilter(props.field) : undefined,
    )
</script>

<style scoped lang="scss">
    .date {
        display: inline-block;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        vertical-align: bottom;
    }
</style>
