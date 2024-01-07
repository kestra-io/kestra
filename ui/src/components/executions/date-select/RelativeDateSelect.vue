<template>
    <date-select
        :value="startRange"
        :options="applicableFilterPresets"
        :tooltip="$t('relative start date')"
        @change="onChangeStart($event)"
    />
    <date-select
        :value="endRange"
        :options="applicableFilterPresetsEnd"
        :tooltip="$t('relative end date')"
        @change="onChangeEnd($event)"
    />
</template>

<script>
    import DateSelect from "./DateSelect.vue";

    export default {
        components: {
            DateSelect
        },
        emits: [
            "update:modelValue"
        ],
        data() {
            return {
                timeFilterPresets: [
                    {
                        value: "PT5M",
                        label: "datepicker.last5minutes"
                    },
                    {
                        value: "PT15M",
                        label: "datepicker.last15minutes"
                    },
                    {
                        value: "PT1H",
                        label: "datepicker.last1hour"
                    },
                    {
                        value: "PT12H",
                        label: "datepicker.last12hours"
                    },
                    {
                        value: "P1D",
                        label: "datepicker.last24hours"
                    },
                    {
                        value: "P1W",
                        label: "datepicker.thisWeekSoFar"
                    },
                    {
                        value: "P1M",
                        label: "datepicker.thisMonthSoFar"
                    },
                    {
                        value: "P1Y",
                        label: "datepicker.thisYearSoFar"
                    }
                ]
            }
        },
        props: {
            startRange: {
                type: String,
                default: undefined
            },
            endRange: {
                type: String,
                default: undefined
            }
        },
        computed: {
            timeFilterPresetsEnd() {
                return [ {value: "PT0", label: "now"} ].concat(this.timeFilterPresets);
            },
            applicableFilterPresets() {
                return this.timeFilterPresets.filter((filterPreset) => this.$moment.duration(filterPreset.value) > this.$moment.duration(this.endRange));
            },
            applicableFilterPresetsEnd() {
                return this.timeFilterPresetsEnd.filter((filterPreset) => this.$moment.duration(filterPreset.value) < this.$moment.duration(this.startRange));
            }
        },
        methods: {
            onChangeStart(range) {
                this.$emit("update:modelValue", {"startDateRange": range, "endDateRange": this.endRange});
            },
            onChangeEnd(range) {
                this.$emit("update:modelValue", {"startDateRange": this.startRange, "endDateRange": range});
            }
        }
    }
</script>