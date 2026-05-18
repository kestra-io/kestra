<template>
    <KsDatePicker
        :modelValue="date"
        @update:model-value="onDate"
        type="datetimerange"
        :shortcuts="shortcuts"
        :startPlaceholder="t('start date')"
        :endPlaceholder="t('end date')"
    />
</template>

<script setup lang="ts">
    import moment from "moment"
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n({useScope: "global"})

    const props = defineProps<{
        startDate?: string
        endDate?: string
    }>()

    const emit = defineEmits<{
        "update:modelValue": [{ startDate?: string; endDate?: string }]
    }>()

    const shortcuts = [
        {
            text: t("datepicker.today"),
            value: () => ([
                moment().startOf("day").toDate(),
                moment().endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.yesterday"),
            value: () => ([
                moment().add(-1, "day").startOf("day").toDate(),
                moment().add(-1, "day").endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.dayBeforeYesterday"),
            value: () => ([
                moment().add(-2, "day").startOf("day").toDate(),
                moment().add(-2, "day").endOf("day").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisWeek"),
            value: () => ([
                moment().startOf("isoWeek").toDate(),
                moment().endOf("isoWeek").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousWeek"),
            value: () => ([
                moment().add(-1, "week").startOf("isoWeek").toDate(),
                moment().add(-1, "week").endOf("isoWeek").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisMonth"),
            value: () => ([
                moment().startOf("month").toDate(),
                moment().endOf("month").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousMonth"),
            value: () => ([
                moment().add(-1, "month").startOf("month").toDate(),
                moment().add(-1, "month").endOf("month").toDate(),
            ]),
        },
        {
            text: t("datepicker.thisYear"),
            value: () => ([
                moment().startOf("year").toDate(),
                moment().endOf("year").toDate(),
            ]),
        },
        {
            text: t("datepicker.previousYear"),
            value: () => ([
                moment().add(-1, "year").startOf("year").toDate(),
                moment().add(-1, "year").endOf("year").toDate(),
            ]),
        },
    ]

    const date = computed<[Date, Date]>(() => [new Date(props.startDate!), new Date(props.endDate!)])

    function onDate(value: [Date, Date] | null) {
        emit("update:modelValue", {
            startDate: value != null && value[0] ? moment(value[0]).toISOString(true) : undefined,
            endDate: value != null && value[1] ? moment(value[1]).toISOString(true) : undefined,
        })
    }
</script>
