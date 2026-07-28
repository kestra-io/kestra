<template>
    <KsTooltip v-if="props.relative && date" :content="absolute">
        <span>{{ date }}</span>
    </KsTooltip>
    <span v-else>{{ date }}</span>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import moment from "moment"
    import {storageKeys} from "../../../../../utils/constants"
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

    const momentLongDateFormat = "llll"
    const format = localStorage.getItem(storageKeys.DATE_FORMAT_STORAGE_KEY) ?? momentLongDateFormat

    const date = computed(() => {
        if (!props.field) return undefined
        // moment(date) always return a Moment, if the date is undefined, it will return current date, we don't want that here
        return props.relative
            ? moment(props.field).calendar(null, {sameElse: "L [at] LT"})
            : moment(props.field).format(format)
    })

    const absolute = computed(() =>
        props.field ? moment(props.field).format(format) : undefined,
    )
</script>
