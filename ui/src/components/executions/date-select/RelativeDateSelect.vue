<template>
    <date-select
        :placeholder="placeholder"
        :value="timeRange"
        :options="timeFilterPresets"
        :tooltip="$t('relative start date')"
        :clearable="clearable"
        @change="onChangeRange"
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
                        label: this.label("5minutes")
                    },
                    {
                        value: "PT15M",
                        label: this.label("15minutes")
                    },
                    {
                        value: "PT1H",
                        label: this.label("1hour")
                    },
                    {
                        value: "PT12H",
                        label: this.label("12hours")
                    },
                    {
                        value: "P1D",
                        label: this.label("24hours")
                    },
                    {
                        value: "P2D",
                        label: this.label("48hours")
                    },
                    {
                        value: "P7D",
                        label: this.label("7days")
                    },
                    {
                        value: "P30D",
                        label: this.label("30days")
                    },
                    {
                        value: "P365D",
                        label: this.label("365days")
                    }
                ]
            }
        },
        props: {
            placeholder: {
                type: String,
                default: undefined
            },
            timeRange: {
                type: String,
                default: undefined
            },
            past: {
                type: Boolean,
                default: true
            },
            allowInfinite: {
                type: Boolean,
                default: false
            },
            clearable: {
                type: Boolean,
                default: false
            }
        },
        methods: {
            onChangeRange(range) {
                this.$emit("update:modelValue", {"timeRange": range});
            },
            label(duration) {
                return "datepicker." + (this.past ? "last" : "in") + duration;
            }
        }
    }
</script>