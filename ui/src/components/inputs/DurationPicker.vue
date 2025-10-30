<template>
    <div class="input-group">
        <label for="years">{{ $t('years') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="years"
            v-model="years"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="months">{{ $t('months') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="months"
            v-model="months"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="weeks">{{ $t('weeks') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="weeks"
            v-model="weeks"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="days">{{ $t('days') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="days"
            v-model="days"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="hours">{{ $t('hours') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="hours"
            v-model="hours"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="minutes">{{ $t('minutes') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="minutes"
            v-model="minutes"
            :min="0"
        />
    </div>
    <div class="input-group">
        <label for="seconds">{{ $t('seconds') }}</label>
        <el-input-number
            size="small"
            controlsPosition="right"
            id="seconds"
            v-model="seconds"
            :min="0"
        />
    </div>
    <div>
        <el-text size="small" :type="durationIssue ? 'danger': ''">
            {{ durationIssue ?? $t('input_custom_duration') }}
        </el-text>
        <el-input type="text" id="customDuration" v-model="customDuration" @input="parseDuration" :placeholder="$t('datepicker.custom duration')" />
    </div>
</template>

<script setup lang="ts">
    import {Duration, Period} from "@js-joda/core";
    import {ref, watch, onMounted, onUpdated} from "vue";

    const props = defineProps<{
        modelValue?: string;
    }>();

    const emit = defineEmits<{
        "update:model-value": [value: string | null];
    }>();

    const years = ref<number>(0);
    const months = ref<number>(0);
    const weeks = ref<number>(0);
    const days = ref<number>(0);
    const hours = ref<number>(0);
    const minutes = ref<number>(0);
    const seconds = ref<number>(0);
    const customDuration = ref<string>("");
    const durationIssue = ref<string | null>(null);

    const updateDuration = () => {
        let duration = "P"
        if (years.value > 0) {
            duration += `${years.value}Y`;
        }
        if (months.value > 0) {
            duration += `${months.value}M`;
        }
        if (weeks.value > 0) {
            duration += `${weeks.value}W`;
        }
        if (days.value > 0) {
            duration += `${days.value}D`;
        }
        if (hours.value > 0 || minutes.value > 0 || seconds.value > 0) {
            duration += "T"
            if (hours.value > 0) {
                duration += `${hours.value}H`;
            }
            if (minutes.value > 0) {
                duration += `${minutes.value}M`;
            }
            if (seconds.value > 0) {
                duration += `${seconds.value}S`;
            }
        }

        let finalDuration: string | null = duration;
        if (duration === "P") {
            finalDuration = null;
        }

        customDuration.value = finalDuration ?? "";
        durationIssue.value = null;
        emit("update:model-value", finalDuration);
    };

    const parseDuration = (durationString: string) => {
        customDuration.value = durationString;
        const [datePart, timePart] = durationString.includes("T") ? durationString.split("T") : [durationString, null];
        let durationIssueMessage: string | null = null;

        try {
            if (datePart && datePart !== "P") {
                const period = Period.parse(datePart);
                years.value = period.years();
                months.value = period.months();
                const parsedDays = period.days();

                weeks.value = Math.floor(parsedDays / 7);
                days.value = parsedDays % 7;
            } else {
                years.value = 0; months.value = 0; weeks.value = 0; days.value = 0;
            }

            if (timePart) {
                const timeDuration = Duration.parse(`PT${timePart}`);
                hours.value = timeDuration.toHours();
                minutes.value = timeDuration.toMinutes() % 60;
                seconds.value = timeDuration.seconds() % 60;
            } else {
                hours.value = 0; minutes.value = 0; seconds.value = 0;
            }

        } catch (e) {
            durationIssueMessage = (e as Error).message;
            emit("update:model-value", null);
        }

        durationIssue.value = durationIssueMessage;
    };

    watch(years, updateDuration);
    watch(months, updateDuration);
    watch(weeks, updateDuration);
    watch(days, updateDuration);
    watch(hours, updateDuration);
    watch(minutes, updateDuration);
    watch(seconds, updateDuration);

    onMounted(() => {
        parseDuration(props.modelValue ?? "");
        updateDuration();
    });

    onUpdated(() => {
        if (props.modelValue) {
            parseDuration(props.modelValue);
            updateDuration();
        }
    });
</script>

<style scoped>
    .input-group {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 80px;
        margin-left: 0.5rem;
    }
</style>