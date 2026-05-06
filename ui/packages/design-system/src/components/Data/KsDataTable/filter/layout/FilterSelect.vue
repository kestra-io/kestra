<template>
    <div class="select-panel">
        <TimeRangeSwitch v-if="filterKey?.key === 'timeRange'" v-model="local.timeRangeMode" />

        <div v-if="local.timeRangeMode === 'predefined'" class="section">
            <KsSelect
                v-model="local.value"
                :placeholder="placeholder ?? $t('filter.select_option')"
                :showArrow="false"
            >
                <template #label="{value}">
                    {{ options.find(opt => opt.value === value)?.label ?? value }}
                </template>
                <KsOption
                    v-for="option in options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                >
                    <span v-if="option.color" class="color-option">
                        <span
                            class="color-dot"
                            :style="{backgroundColor: option.color}"
                        />
                        {{ option.label }}
                    </span>
                </KsOption>
            </KsSelect>
        </div>

        <div v-else class="section">
            <div class="date-field">
                <label class="form-label">{{ $t("filter.start_date") }}</label>
                <KsDatePicker
                    v-model="local.startDateValue"
                    type="datetime"
                    :placeholder="$t('filter.select_start_date')"
                />
            </div>
            <div class="date-field">
                <label class="form-label">{{ $t("filter.end_date") }}</label>
                <KsDatePicker
                    v-model="local.endDateValue"
                    type="datetime"
                    :placeholder="$t('filter.select_end_date')"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {reactive, toRefs, watchEffect} from "vue";
    import TimeRangeSwitch from "./TimeRangeSwitch.vue";

    const props = defineProps<{
        label?: string;
        modelValue: string;
        placeholder?: string;
        filterKey?: {key: string};
        endDateValue?: Date | null;
        startDateValue?: Date | null;
        timeRangeMode?: "predefined" | "custom";
        options: {value: string; label: string; color?: string}[];
    }>();

    const emit = defineEmits<{
        "update:modelValue": [value: string];
        "update:endDateValue": [date: Date | null];
        "update:startDateValue": [date: Date | null];
        "update:timeRangeMode": [mode: "predefined" | "custom"];
    }>();

    const {modelValue, timeRangeMode, startDateValue, endDateValue} = toRefs(props);

    const local = reactive({
        value: modelValue.value,
        endDateValue: endDateValue.value ?? null,
        startDateValue: startDateValue.value ?? null,
        timeRangeMode: timeRangeMode.value ?? "predefined"
    });

    watchEffect(() => {
        local.value = modelValue.value;
        local.endDateValue = endDateValue.value ?? null;
        local.startDateValue = startDateValue.value ?? null;
        local.timeRangeMode = timeRangeMode.value ?? "predefined";
    });

    watchEffect(() => {
        emit("update:modelValue", local.value);
        emit("update:timeRangeMode", local.timeRangeMode);
        emit("update:endDateValue", local.endDateValue);
        emit("update:startDateValue", local.startDateValue);
    });
</script>

<style lang="scss" scoped>
.select-panel {
    .section {
        padding: 1rem;

        .date-field {
            &:not(:last-child) {
                margin-bottom: 0.5rem;
            }

            .form-label {
                display: block;
                color: var(--ks-content-secondary);
                font-size: var(--ks-font-size-xs);
                font-weight: 500;
                margin-bottom: 0.25rem;
            }
        }
    }
}

:deep(.kel-date-editor) {
    .kel-input__inner::placeholder {
        color: var(--ks-content-tertiary);
        font-size: var(--ks-font-size-sm);
    }

    .kel-input__prefix .kel-input__icon {
        color: var(--ks-content-tertiary);
        font-size: var(--ks-font-size-base);
    }
}

.kel-select-dropdown__item {
    font-size: var(--ks-font-size-sm);
}

.color-option {
    display: flex;
    align-items: center;
    gap: 0.5rem;

    .color-dot {
        display: inline-block;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
    }
}
</style>
