<template>
    <div class="select-panel">
        <div class="switch-container">
            <div class="switch-wrapper">
                <input
                    id="time-range-switch"
                    type="checkbox"
                    :checked="localTimeRangeMode === 'custom'"
                    @change="toggleTimeRangeMode"
                >
                <label for="time-range-switch" class="switch-label">
                    <span class="switch-option left">Predefined</span>
                    <span class="switch-option right">Custom Range</span>
                    <div class="switch-slider" />
                </label>
            </div>
        </div>

        <div v-if="localTimeRangeMode === 'predefined'" class="section">
            <el-select
                v-model="localValue"
                :placeholder="placeholder ?? 'Select an option'"
                class="full-width"
                :filterable="searchable"
                :showArrow="false"
            >
                <el-option
                    v-for="option in options"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                />
            </el-select>
        </div>

        <div v-else class="section">
            <div class="date-field">
                <label class="form-label">Start Date</label>
                <el-date-picker
                    v-model="localStartDateValue"
                    type="datetime"
                    placeholder="Select start date"
                    class="full-width"
                />
            </div>
            <div class="date-field">
                <label class="form-label">End Date</label>
                <el-date-picker
                    v-model="localEndDateValue"
                    type="datetime"
                    placeholder="Select end date"
                    class="full-width"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watchEffect, toRefs} from "vue";

    interface FilterValue {
        value: string;
        label: string;
    }

    interface Props {
        modelValue: string;
        options: FilterValue[];
        searchable?: boolean;
        placeholder?: string;
        label?: string;
        timeRangeMode?: "predefined" | "custom";
        startDateValue?: Date | null;
        endDateValue?: Date | null;
    }

    interface Emits {
        "update:modelValue": [value: string];
        "update:timeRangeMode": [mode: "predefined" | "custom"];
        "update:startDateValue": [date: Date | null];
        "update:endDateValue": [date: Date | null];
    }

    const props = defineProps<Props>();
    const emit = defineEmits<Emits>();

    const {
        modelValue,
        options,
        searchable,
        placeholder,
        timeRangeMode,
        startDateValue,
        endDateValue
    } = toRefs(props);

    const localValue = ref(modelValue.value);
    const localTimeRangeMode = ref<"predefined" | "custom">(timeRangeMode.value ?? "predefined");
    const localStartDateValue = ref<Date | null>(startDateValue.value ?? null);
    const localEndDateValue = ref<Date | null>(endDateValue.value ?? null);

    watchEffect(() => {
        localValue.value = modelValue.value;
        localTimeRangeMode.value = timeRangeMode.value ?? "predefined";
        localStartDateValue.value = startDateValue.value ?? null;
        localEndDateValue.value = endDateValue.value ?? null;
    });

    watchEffect(() => {
        emit("update:modelValue", localValue.value);
        emit("update:timeRangeMode", localTimeRangeMode.value);
        emit("update:startDateValue", localStartDateValue.value);
        emit("update:endDateValue", localEndDateValue.value);
    });

    const toggleTimeRangeMode = (event: Event) => {
        const target = event.target as HTMLInputElement;
        localTimeRangeMode.value = target.checked ? "custom" : "predefined";
    };
</script>

<style lang="scss" scoped>
.select-panel {
    .switch-container {
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 0.5rem;
        padding-left: 1rem;
        padding-right: 1rem;
    }

    .section {
        padding: 1rem;

        .full-width {
            width: 100%;
        }

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

    .switch-wrapper {
        display: inline-block;
        position: relative;
        width: 100%;

        input[type="checkbox"] {
            opacity: 0;
            position: absolute;
            z-index: -1;

            &:checked ~ .switch-label .switch-slider {
                transform: translateX(100%);
            }
        }
    }

    .switch-label {
        align-items: center;
        background-color: var(--ks-background-body);
        border: 1px solid var(--ks-border-primary);
        border-radius: 20px;
        cursor: pointer;
        display: flex;
        padding: 4px;
        position: relative;
        transition: all 0.3s ease;
        user-select: none;
        justify-content: space-around;
    }

    .switch-option {
        color: var(--ks-content-primary);
        font-size: 12px;
        font-weight: 500;
        padding: 6px 16px;
        position: relative;
        transition: color 0.3s ease;
        white-space: nowrap;
        z-index: 2;
    }

    .switch-slider {
        background-color: var(--ks-background-card);
        border-radius: 16px;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
        height: calc(100% - 8px);
        left: 4px;
        position: absolute;
        top: 4px;
        transition: transform 0.3s ease;
        width: calc(50% - 4px);
        z-index: 1;
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
</style>
