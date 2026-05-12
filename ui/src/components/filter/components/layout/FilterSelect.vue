<template>
    <div class="select-panel">
        <TimeRangeSwitch v-if="filterKey?.key === 'timeRange'" v-model="local.timeRangeMode" />

        <div v-if="local.timeRangeMode === 'predefined'" class="section">
            <el-select
                v-model="local.value"
                :placeholder="placeholder ?? $t('filter.select_option')"
                :showArrow="false"
            >
                <el-option
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
                </el-option>
            </el-select>
        </div>

        <div v-else class="section">
            <div class="date-field">
                <label class="form-label">{{ $t("filter.start_date") }}</label>
                <el-date-picker
                    v-model="local.startDateValue"
                    type="datetime"
                    :placeholder="$t('filter.select_start_date')"
                />
            </div>
            <div class="date-field">
                <label class="form-label">{{ $t("filter.end_date") }}</label>
                <el-date-picker
                    v-model="local.endDateValue"
                    type="datetime"
                    :placeholder="$t('filter.select_end_date')"
                />
            </div>
        </div>

        <div v-if="filterKey?.dateFilterOptions?.length" class="section date-filter-section">
            <label class="form-label">{{ $t("filter.timeRange.applyTo") }}</label>
            <div class="date-filter-options">
                <button
                    v-for="opt in filterKey.dateFilterOptions"
                    :key="opt.value"
                    class="date-filter-option"
                    :class="{active: local.dateFilterMode === opt.value}"
                    type="button"
                    @click="local.dateFilterMode = opt.value"
                >
                    {{ opt.label }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {reactive, toRefs, watchEffect} from "vue";
    import TimeRangeSwitch from "./TimeRangeSwitch.vue";
    import type {DateFilterOption} from "../utils/filterTypes";

    const props = defineProps<{
        label?: string;
        modelValue: string;
        placeholder?: string;
        filterKey?: {key: string; dateFilterOptions?: DateFilterOption[]};
        endDateValue?: Date | null;
        startDateValue?: Date | null;
        timeRangeMode?: "predefined" | "custom";
        dateFilterMode?: string;
        options: {value: string; label: string; color?: string}[];
    }>();

    const emit = defineEmits<{
        "update:modelValue": [value: string];
        "update:endDateValue": [date: Date | null];
        "update:startDateValue": [date: Date | null];
        "update:timeRangeMode": [mode: "predefined" | "custom"];
        "update:dateFilterMode": [mode: string];
    }>();

    const {modelValue, timeRangeMode, startDateValue, endDateValue, dateFilterMode} = toRefs(props);

    const local = reactive({
        value: modelValue.value,
        endDateValue: endDateValue.value ?? null,
        startDateValue: startDateValue.value ?? null,
        timeRangeMode: timeRangeMode.value ?? "predefined",
        dateFilterMode: dateFilterMode.value ?? props.filterKey?.dateFilterOptions?.[0]?.value ?? "",
    });

    watchEffect(() => {
        local.value = modelValue.value;
        local.endDateValue = endDateValue.value ?? null;
        local.startDateValue = startDateValue.value ?? null;
        local.timeRangeMode = timeRangeMode.value ?? "predefined";
        if (dateFilterMode.value !== undefined) {
            local.dateFilterMode = dateFilterMode.value;
        }
    });

    watchEffect(() => {
        emit("update:modelValue", local.value);
        emit("update:timeRangeMode", local.timeRangeMode);
        emit("update:endDateValue", local.endDateValue);
        emit("update:startDateValue", local.startDateValue);
        emit("update:dateFilterMode", local.dateFilterMode);
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
                font-size: 0.75rem;
                font-weight: 500;
                margin-bottom: 0.25rem;
            }
        }
    }

    .date-filter-section {
        border-top: 1px solid var(--ks-border-primary);
        padding-top: 0.75rem;

        .form-label {
            display: block;
            color: var(--ks-content-secondary);
            font-size: var(--ks-font-size-xs);
            font-weight: 500;
            margin-bottom: 0.5rem;
        }

        .date-filter-options {
            display: flex;
            gap: 0.25rem;
            flex-wrap: wrap;
        }

        .date-filter-option {
            background: var(--ks-background-body);
            border: 1px solid var(--ks-border-primary);
            border-radius: var(--ks-border-radius-sm);
            color: var(--ks-content-primary);
            cursor: pointer;
            font-size: var(--ks-font-size-xs);
            font-weight: 500;
            padding: 4px 10px;
            transition: background 0.15s, border-color 0.15s;

            &:hover {
                background: var(--ks-background-card);
            }

            &.active {
                background: var(--ks-background-card);
                border-color: var(--ks-primary);
                color: var(--ks-primary);
            }
        }
    }
}

:deep(.el-date-editor) {
    .el-input__inner::placeholder {
        color: var(--ks-content-tertiary);
        font-size: 14px;
    }

    .el-input__prefix .el-input__icon {
        color: var(--ks-content-tertiary);
        font-size: 16px;
    }
}

.el-select-dropdown__item {
    font-size: 14px;
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
