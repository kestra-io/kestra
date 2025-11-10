<template>
    <DateSelect
        :placeholder="customAwarePlaceholder"
        :value="timeRangeSelect"
        :options="timeFilterPresets"
        :tooltip="fromNow ? $t('relative start date') : undefined"
        :clearable="clearable"
        @change="onTimeRangeSelect"
    />
    <el-tooltip v-if="allowCustom && timeRangeSelect === undefined" :content="allowInfinite ? $t('datepicker.leave empty for infinite') : $t('datepicker.duration example')">
        <el-input class="mt-2" :modelValue="timeRange" :placeholder="$t('datepicker.custom duration')" @update:model-value="onTimeRangeChange" />
    </el-tooltip>
</template>

<script lang="ts" setup>
    import {ref, computed, watch, PropType} from "vue";
    import DateSelect from "./DateSelect.vue";
    import {useI18n} from "vue-i18n";

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
        includeNever: {type: Boolean, default: false}
    })

    const timeRangeSelect = ref<string | undefined>(undefined);

    const label = (duration: string): string =>
        "datepicker." + (props.fromNow ? "last" : "") + duration;

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
            {value: "PT8760H", label: label("365days")}
        ];

        if (props.includeNever) {
            values.push({value: undefined, label: "datepicker.never"});
        }

        return values;
    });

    const presetValues = computed<(string | undefined)[]>(() =>
        timeFilterPresets.value.map(preset => preset.value)
    );

    const {t} = useI18n();

    const customAwarePlaceholder = computed<string | undefined>(() => {
        if (props.placeholder) return props.placeholder;
        return props.allowCustom ? t("datepicker.custom") : undefined;
    });

    const onTimeRangeSelect = (range: string | undefined) => {
        timeRangeSelect.value = range;
        onTimeRangeChange(range);
    };

    const emit = defineEmits<{
        (e: "update:modelValue", payload: { timeRange: string | undefined }): void;
    }>();

    const onTimeRangeChange = (range: string | undefined) => {
        emit("update:modelValue", {timeRange: range});
    };

    // Watcher
    watch(
        () => props.timeRange,
        (newValue, oldValue) => {
            if (oldValue === undefined && presetValues.value.includes(newValue)) {
                onTimeRangeSelect(newValue);
            }
        },
        {immediate: true}
    );
</script>
