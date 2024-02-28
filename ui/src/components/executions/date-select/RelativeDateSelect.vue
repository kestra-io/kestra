<template>
    <date-select
        :value="timeRange"
        :options="timeFilterPresets"
        :tooltip="$t('relative start date')"
        @change="onChangeRange($event)"
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
                        value: "P7D",
                        label: "datepicker.last7days"
                    },
                    {
                        value: "P30D",
                        label: "datepicker.last30days"
                    },
                    {
                        value: "P365D",
                        label: "datepicker.last365days"
                    }
                ]
            }
        },
        props: {
            timeRange: {
                type: String,
                default: undefined
            }
        },
        methods: {
            onChangeRange(range) {
                this.$emit("update:modelValue", {"timeRange": range});
            },
        }
    }
</script>