<template>
    <date-select
        :placeholder="customAwarePlaceholder"
        :value="timeRangeSelect"
        :options="timeFilterPresets"
        :tooltip="fromNow ? $t('relative start date') : undefined"
        :clearable="clearable"
        @change="onTimeRangeSelect"
    />
    <el-tooltip v-if="allowCustom && timeRangeSelect === undefined" :content="allowInfinite ? $t('datepicker.leave empty for infinite') : $t('datepicker.duration example')">
        <el-input class="mt-2" :model-value="timeRange" :placeholder="$t('datepicker.custom duration')" @update:model-value="onTimeRangeChange" />
    </el-tooltip>
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
        computed: {
            customAwarePlaceholder() {
                if (this.placeholder) {
                    return this.placeholder;
                }
                return this.allowCustom ? this.$t("datepicker.custom") : undefined;
            },
            timeFilterPresets(){
                let values = [
                    {value: "PT5M", label: this.label("5minutes")},
                    {value: "PT15M", label: this.label("15minutes")},
                    {value: "PT1H", label: this.label("1hour")},
                    {value: "PT12H", label: this.label("12hours")},
                    {value: "PT24H", label: this.label("24hours")},
                    {value: "PT48H", label: this.label("48hours")},
                    {value: "PT168H", label: this.label("7days")},
                    {value: "PT720H", label: this.label("30days")},
                    {value: "PT8760H", label: this.label("365days")},
                ]
                
                if(this.includeNever){
                    values.push({value: undefined, label: this.$t("datepicker.never")})
                }
                
                return values;
            },
            presetValues() {
                return this.timeFilterPresets.map(preset => preset.value);
            }
        },
        watch: {
            timeRange: {
                handler(newValue, oldValue) {
                    if (oldValue === undefined && this.presetValues.includes(newValue)) {
                        this.onTimeRangeSelect(newValue);
                    }
                },
                immediate: true
            }
        },
        data() {
            return {
                timeRangeSelect: undefined
            }
        },
        props: {
            allowCustom: {
                type: Boolean,
                default: false
            },
            placeholder: {
                type: String,
                default: undefined
            },
            timeRange: {
                type: String,
                default: undefined
            },
            fromNow: {
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
            },
            includeNever: {
                type: Boolean,
                default: false
            }
        },
        methods: {
            onTimeRangeSelect(range) {
                this.timeRangeSelect = range;
                this.onTimeRangeChange(range);
            },
            onTimeRangeChange(range) {
                this.$emit("update:modelValue", {"timeRange": range});
            },
            label(duration) {
                return "datepicker." + (this.fromNow ? "last" : "") + duration;
            }
        }
    }
</script>