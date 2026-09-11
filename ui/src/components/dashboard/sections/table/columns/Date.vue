<template>
    <KsTooltip v-if="date" :content="absolute">
        <span class="date">{{ date }}</span>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import moment from "moment-timezone"
    import {storageKeys} from "../../../../../utils/constants"
    import {date as dateFilter} from "../../../../../utils/filters"
    import {KsTooltip} from "@kestra-io/design-system"

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

    // The relative branch needs moment's calendar(), which dateFilter cannot express, so it applies
    // the stored timezone itself rather than falling back to the machine's.
    const inTimezone = (value: string) =>
        moment(value).tz(localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())

    const date = computed(() => {
        if (!props.field) return undefined
        // moment(date) always return a Moment, if the date is undefined, it will return current date, we don't want that here
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
