<template>
    <date-picker
        @input="onDate($event)"
        :value="date"
        :required="false"
        :shortcuts="shortcuts"
        :lang="lang"
        type="datetime"
        class="date-range"
        input-class="form-control"
        range
        :placeholder="$t('date')"
    />
</template>
<script>
    import DatePicker from "vue2-datepicker";
    import moment from "moment";

    export default {
        components: {DatePicker},
        emits: ["input"],
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
                        text: this.$t("datepicker.last1hour"),
                        onClick: () => [
                            this.$moment().add(-1, "hour").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.last12hours"),
                        onClick: () => [
                            this.$moment().add(-12, "hour").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.last24hours"),
                        onClick: () => [
                            this.$moment().add(-1, "day").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.today"),
                        onClick: () => [
                            this.$moment().startOf("day").toDate(),
                            this.$moment().endOf("day").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.yesterday"),
                        onClick: () => [
                            this.$moment().add(-1, "day").startOf("day").toDate(),
                            this.$moment().add(-1, "day").endOf("day").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.dayBeforeYesterday"),
                        onClick: () => [
                            this.$moment().add(-2, "day").startOf("day").toDate(),
                            this.$moment().add(-2, "day").endOf("day").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisWeek"),
                        onClick: () => [
                            this.$moment().startOf("isoWeek").toDate(),
                            this.$moment().endOf("isoWeek").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisWeekSoFar"),
                        onClick: () => [
                            this.$moment().add(-1, "isoWeek").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.previousWeek"),
                        onClick: () => [
                            this.$moment().add(-1, "week").startOf("isoWeek").toDate(),
                            this.$moment().add(-1, "week").endOf("isoWeek").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisMonth"),
                        onClick: () => [
                            this.$moment().startOf("month").toDate(),
                            this.$moment().endOf("month").toDate(),
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisMonthSoFar"),
                        onClick: () => [
                            this.$moment().add(-1, "month").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.previousMonth"),
                        onClick: () => [
                            this.$moment().add(-1, "month").startOf("month").toDate(),
                            this.$moment().add(-1, "month").endOf("month").toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisYear"),
                        onClick: () => [
                            this.$moment().startOf("year").toDate(),
                            this.$moment().endOf("year").toDate(),
                        ],
                    },
                    {
                        text: this.$t("datepicker.thisYearSoFar"),
                        onClick: () => [
                            this.$moment().add(-1, "year").toDate(),
                            this.$moment().toDate()
                        ],
                    },
                    {
                        text: this.$t("datepicker.previousYear"),
                        onClick: () => [
                            this.$moment().add(-1, "year").startOf("year").toDate(),
                            this.$moment().add(-1, "year").endOf("year").toDate()
                        ],
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
                this.$emit("input", {
                    "startDate": value[0] ? moment(value[0]).toISOString(true) : undefined,
                    "endDate": value[1] ? moment(value[1]).toISOString(true) : undefined
                });
            }
        },
        computed: {
            date() {
                return [new Date(this.startDate), new Date(this.endDate)];
            }
        }
    };
</script>
<style scoped lang="scss">
.time-line {
    margin-left: 10px;
    margin-right: 10px;
}
:deep(.mx-datepicker) {
    margin-right: 5px;

}

:deep(.mx-datepicker-popup) {
    height: 272px;

    .mx-datepicker-sidebar {
        overflow-y: auto;
    }
}
</style>

<style lang="scss">
@import "../../styles/_variable.scss";
.mx-datepicker-popup {
    height: 272px;

    .mx-datepicker-sidebar {
        width: 150px;
        & + .mx-datepicker-content {
            margin-left: 150px;
        }

        .mx-btn {
            font-size: $font-size-xs;
        }
        max-height: 100%;
        overflow-y: auto;

        &::-webkit-scrollbar {
            width: 4px;
            height: 4px;
        }

        &::-webkit-scrollbar-track {
            background-color: var(--gray-100);
        }

        &::-webkit-scrollbar-thumb {
            background-color: var(--gray-300);
        }
    }
}
</style>
