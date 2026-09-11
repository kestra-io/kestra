<template>
    <DateSelect
        :placeholder="customAwarePlaceholder"
        :value="timeRangeSelect"
        :options="timeFilterPresets"
        :tooltip="fromNow ? $t('relative start date') : undefined"
        :clearable="clearable"
        @change="onTimeRangeSelect"
    />
    <KsTooltip v-if="allowCustom && timeRangeSelect === undefined">
        <template #content>
            <span v-if="allowInfinite">{{ $t('datepicker.leave empty for infinite') }}</span>
            <span v-else>{{ $t('datepicker.duration example') }}</span>
            <div class="mt-2 duration-examples">
                <strong>{{ $t("datepicker.examples") }}</strong>
                <table class="duration-table">
                    <tbody>
                        <tr v-for="example in DURATION_EXAMPLES" :key="example">
                            <td>{{ example }}</td><td>&rarr; {{ humanExample(example) }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </template>
        <KsInput class="mt-2" data-test="custom-duration" :modelValue="timeRange" :placeholder="$t('datepicker.custom duration')" @update:model-value="onTimeRangeChange" />
    </KsTooltip>
</template>

<script lang="ts" setup>
    import {ref, computed, watch, PropType} from "vue"
    import DateSelect from "./DateSelect.vue"
    import {useI18n} from "vue-i18n"
    import {durationUtils} from "@kestra-io/design-system"

    interface TimePreset {
        value?: string;
        label: string;
    }

    defineOptions({
        name: "TimeRangePicker",
    })

    const props = defineProps({
        allowCustom: {type: Boolean, default: false},
        placeholder: {type: String as PropType<string | undefined>, default: undefined},
        timeRange: {type: String as PropType<string | undefined>, default: undefined},
        fromNow: {type: Boolean, default: true},
        allowInfinite: {type: Boolean, default: false},
        clearable: {type: Boolean, default: false},
        includeNever: {type: Boolean, default: false},
    })

    const timeRangeSelect = ref<string | undefined>(undefined)

    const DURATION_EXAMPLES = ["PT30M", "PT1H", "P1D", "P7D", "P30D", "P1DT2H"]
    const humanExample = (iso: string) => durationUtils.humanDuration(iso, {units: ["d", "h", "m"]})

    const label = (duration: string): string =>
        "datepicker." + (props.fromNow ? "last" : "") + duration

    const timeFilterPresets = computed<TimePreset[]>(() => {
        const values: TimePreset[] = [
            {value: "PT5M", label: label("5minutes")},
            {value: "PT15M", label: label("15minutes")},
            {value: "PT1H", label: label("1hour")},
            {value: "PT12H", label: label("12hours")},
            {value: "PT24H", label: label("24hours")},
            {value: "PT48H", label: label("48hours")},
            {value: "PT168H", label: label("7days")},
            {value: "PT720H", label: label("30days")},
            {value: "PT8760H", label: label("365days")},
        ]

        if (props.includeNever) {
            values.push({value: undefined, label: "datepicker.never"})
        }

        return values
    })

    const presetValues = computed<(string | undefined)[]>(() =>
        timeFilterPresets.value.map(preset => preset.value),
    )

    const {t} = useI18n()

    const customAwarePlaceholder = computed<string | undefined>(() => {
        if (props.placeholder) return props.placeholder
        return props.allowCustom ? t("datepicker.custom") : undefined
    })

    const onTimeRangeSelect = (range: string | number | undefined) => {
        const strRange = range !== undefined ? String(range) : undefined
        timeRangeSelect.value = strRange
        onTimeRangeChange(strRange)
    }

    const emit = defineEmits<{
        (e: "update:modelValue", payload: { timeRange: string | undefined }): void;
    }>()

    const onTimeRangeChange = (range: string | number | undefined) => {
        const strRange = range !== undefined ? String(range) : undefined
        emit("update:modelValue", {timeRange: strRange})
    }

    // Watcher
    watch(
        () => props.timeRange,
        (newValue, oldValue) => {
            if (oldValue === undefined && presetValues.value.includes(newValue)) {
                onTimeRangeSelect(newValue)
            }
        },
        {immediate: true},
    )
</script>

<style scoped lang="scss">
.duration-examples {
    line-height: 1.5;
}

.duration-table {
    margin-top: 4px;
    border-collapse: collapse;

    td:first-child {
        padding-right: 12px;
    }
}
</style>
