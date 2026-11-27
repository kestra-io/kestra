<template>
    <KsTooltip v-if="date" :content="tooltip">
        <span class="date" :class="{overdue}">{{ overdue ? $t("dashboards.overdue", {date}) : date }}</span>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
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

    const {t} = useI18n({useScope: "global"})

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

    // A due date already past means the trigger is stuck, not that the date is wrong, so the row
    // stays visible and says so rather than reading as an ordinary upcoming run.
    const overdue = computed(() => Boolean(props.relative && props.field && dayjs(props.field).isBefore(dayjs())))

    const absolute = computed(() =>
        props.field ? dateFilter(props.field) : undefined,
    )

    const tooltip = computed(() =>
        overdue.value ? t("dashboards.overdue", {date: absolute.value}) : absolute.value,
    )
</script>

<style scoped>
    .date {
        display: inline-block;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        vertical-align: bottom;

        &.overdue {
            color: var(--ks-text-warning);
        }
    }
</style>
