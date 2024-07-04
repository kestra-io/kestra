<template>
    <el-date-picker
        data-component="FILENAME_PLACEHOLDER"
        :model-value="date"
        @update:model-value="onDate"
        type="datetimerange"
        :shortcuts="shortcuts"
        :start-placeholder="$t('start date')"
        :end-placeholder="$t('end date')"
    />
</template>
<script>
    import moment from "moment";

    export default {
        emits: ["update:modelValue"],
        data() {
            return {
                lang: {
                    formatLocale: {
                        firstDayOfWeek: 1,
                    },
                    monthBeforeYear: false,
                },
                shortcuts: [
                    {
                        text: this.$t("datepicker.today"),
                        value: () => ([
                            this.$moment().startOf("day").toDate(),
                            this.$moment().endOf("day").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.yesterday"),
                        value: () => ([
                            this.$moment().add(-1, "day").startOf("day").toDate(),
                            this.$moment().add(-1, "day").endOf("day").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.dayBeforeYesterday"),
                        value: () => ([
                            this.$moment().add(-2, "day").startOf("day").toDate(),
                            this.$moment().add(-2, "day").endOf("day").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.thisWeek"),
                        value: () => ([
                            this.$moment().startOf("isoWeek").toDate(),
                            this.$moment().endOf("isoWeek").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.previousWeek"),
                        value: () => ([
                            this.$moment().add(-1, "week").startOf("isoWeek").toDate(),
                            this.$moment().add(-1, "week").endOf("isoWeek").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.thisMonth"),
                        value: () => ([
                            this.$moment().startOf("month").toDate(),
                            this.$moment().endOf("month").toDate(),
                        ]),
                    },
                    {
                        text: this.$t("datepicker.previousMonth"),
                        value: () => ([
                            this.$moment().add(-1, "month").startOf("month").toDate(),
                            this.$moment().add(-1, "month").endOf("month").toDate()
                        ]),
                    },
                    {
                        text: this.$t("datepicker.thisYear"),
                        value: () => ([
                            this.$moment().startOf("year").toDate(),
                            this.$moment().endOf("year").toDate(),
                        ]),
                    },
                    {
                        text: this.$t("datepicker.previousYear"),
                        value: () => ([
                            this.$moment().add(-1, "year").startOf("year").toDate(),
                            this.$moment().add(-1, "year").endOf("year").toDate()
                        ]),
                    },
                ],
            }
        },
        props: {
            startDate: {
                type: String,
                default: undefined
            },
            endDate: {
                type: String,
                default: undefined
            }
        },
        methods: {
            onDate(value) {
                this.$emit("update:modelValue", {
                    "startDate": value != null && value[0] ? moment(value[0]).toISOString(true) : undefined,
                    "endDate": value != null && value[1] ? moment(value[1]).toISOString(true) : undefined
                });
            }
        },
        computed: {
            date() {
                return [new Date(this.startDate), new Date(this.endDate)];
            },
        }
    };
</script>
