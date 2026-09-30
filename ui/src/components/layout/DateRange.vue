<template>
    <KsDatePicker
        :modelValue="date"
        @update:model-value="onDate"
        type="datetimerange"
        :shortcuts="shortcuts"
        :startPlaceholder="$t('start date')"
        :endPlaceholder="$t('end date')"
    />
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {dateUtils, dayjs} from "@kestra-io/design-system"

    const props = withDefaults(defineProps<{
        startDate?: string
        endDate?: string
    }>(), {
        startDate: undefined,
        endDate: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [{startDate: string | undefined; endDate: string | undefined}]
    }>()

    const {t} = useI18n()

    const shortcuts = computed(() => [
        {
            text: t("datepicker.today"),
            value: () => ([
                dayjs().startOf("day").toDate(),
                dayjs().endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.yesterday"),
            value: () => ([
                dayjs().add(-1, "day").startOf("day").toDate(),
                dayjs().add(-1, "day").endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.dayBeforeYesterday"),
            value: () => ([
                dayjs().add(-2, "day").startOf("day").toDate(),
                dayjs().add(-2, "day").endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisWeek"),
            value: () => ([
                dayjs().startOf("isoWeek").toDate(),
                dayjs().endOf("isoWeek").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousWeek"),
            value: () => ([
                dayjs().add(-1, "week").startOf("isoWeek").toDate(),
                dayjs().add(-1, "week").endOf("isoWeek").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisMonth"),
            value: () => ([
                dayjs().startOf("month").toDate(),
                dayjs().endOf("month").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousMonth"),
            value: () => ([
                dayjs().add(-1, "month").startOf("month").toDate(),
                dayjs().add(-1, "month").endOf("month").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisYear"),
            value: () => ([
                dayjs().startOf("year").toDate(),
                dayjs().endOf("year").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousYear"),
            value: () => ([
                dayjs().add(-1, "year").startOf("year").toDate(),
                dayjs().add(-1, "year").endOf("year").toDate(),
            ]),
        },
    ])

    const date = computed(() => [new Date(props.startDate!), new Date(props.endDate!)])

    function onDate(value: [Date, Date] | null) {
        emit("update:modelValue", {
            "startDate": value != null && value[0] ? dateUtils.toIsoKeepOffset(dayjs(value[0])) : undefined,
            "endDate": value != null && value[1] ? dateUtils.toIsoKeepOffset(dayjs(value[1])) : undefined,
        })
    }
</script>
