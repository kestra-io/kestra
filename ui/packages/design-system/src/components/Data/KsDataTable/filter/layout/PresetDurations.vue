<template>
    <div class="preset-durations" data-test="preset-durations">
        <KsSegmented
            :modelValue="activeValue"
            :options="options"
            :disabled="!timeRangeConfig || filter.readOnly?.value"
            @change="onChange"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";

    import KsSegmented from "../../../KsSegmented.vue";
    import {
        type AppliedFilter,
        type FilterKeyConfig,
        COMPARATOR_LABELS,
        Comparators,
    } from "../utils/filterTypes";
    import {FILTER_CONTEXT_INJECTION_KEY} from "../utils/filterInjectionKeys";

    export interface PresetDuration {
        label: string;
        value: string;
    }

    const props = defineProps<{
        presets: PresetDuration[]
    }>();

    const filter = inject(FILTER_CONTEXT_INJECTION_KEY)!;

    const TIME_RANGE_KEY = "timeRange";

    const timeRangeConfig = computed<FilterKeyConfig | undefined>(() =>
        filter.configuration?.value?.keys?.find((k) => k.key === TIME_RANGE_KEY),
    );

    const activeValue = computed<string | undefined>(() => {
        const current = filter.appliedFilters?.value?.find((f) => f.key === TIME_RANGE_KEY)?.value;
        if (typeof current !== "string") {
            return undefined;
        }
        return props.presets.some((p) => p.value === current) ? current : undefined;
    });

    const options = computed(() => props.presets.map((p) => ({label: p.label, value: p.value})));

    const onChange = (value: string | number | boolean) => {
        if (!timeRangeConfig.value || filter.readOnly?.value) {
            return;
        }
        if (typeof value !== "string" || value === activeValue.value) {
            return;
        }
        const preset = props.presets.find((p) => p.value === value);
        if (!preset) {
            return;
        }

        const comparator =
            (timeRangeConfig.value.comparators?.[0] as Comparators) ?? Comparators.EQUALS;

        const applied: AppliedFilter = {
            id: `${TIME_RANGE_KEY}-preset-${preset.value}-${Date.now()}`,
            key: TIME_RANGE_KEY,
            keyLabel: timeRangeConfig.value.label,
            comparator,
            comparatorLabel: COMPARATOR_LABELS[comparator],
            value: preset.value,
            valueLabel: preset.label,
        };

        filter.addFilter(applied);
    };
</script>

<style lang="scss" scoped>
    .preset-durations {
        display: inline-flex;
        align-items: center;
    }
</style>
