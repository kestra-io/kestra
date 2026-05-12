<template>
    <div class="ks-duration-picker" v-bind="$attrs">
        <div class="ks-duration-picker__fields">
            <div class="ks-duration-picker__field">
                <label for="ks-duration-years">{{ $t('years') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-years"
                    v-model="years"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-months">{{ $t('months') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-months"
                    v-model="months"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-weeks">{{ $t('weeks') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-weeks"
                    v-model="weeks"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-days">{{ $t('days') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-days"
                    v-model="days"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-hours">{{ $t('hours') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-hours"
                    v-model="hours"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-minutes">{{ $t('minutes') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-minutes"
                    v-model="minutes"
                    :min="0"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-seconds">{{ $t('seconds') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-seconds"
                    v-model="seconds"
                    :min="0"
                />
            </div>
        </div>
        <div class="ks-duration-picker__custom">
            <KsText size="small" :type="durationIssue ? 'danger': ''">
                {{ durationIssue ?? $t('input_custom_duration') }}
            </KsText>
            <KsInput type="text" id="ks-duration-custom" v-model="customDuration" @input="parseDuration" :placeholder="$t('datepicker.custom duration')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | null
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: string | null]
    }>()

    const years = ref(0)
    const months = ref(0)
    const weeks = ref(0)
    const days = ref(0)
    const hours = ref(0)
    const minutes = ref(0)
    const seconds = ref(0)
    const customDuration = ref("")
    const durationIssue = ref<string | null>(null)

    const updateDuration = () => {
        let duration = "P"
        if (years.value > 0) duration += `${years.value}Y`
        if (months.value > 0) duration += `${months.value}M`
        if (weeks.value > 0) duration += `${weeks.value}W`
        if (days.value > 0) duration += `${days.value}D`

        if (hours.value > 0 || minutes.value > 0 || seconds.value > 0) {
            duration += "T"
            if (hours.value > 0) duration += `${hours.value}H`
            if (minutes.value > 0) duration += `${minutes.value}M`
            if (seconds.value > 0) duration += `${seconds.value}S`
        }

        const finalDuration: string | null = duration === "P" ? null : duration
        customDuration.value = finalDuration ?? ""
        durationIssue.value = null
        emit("update:modelValue", finalDuration)
    }

    const parseDuration = (durationString: string) => {
        customDuration.value = durationString

        if (!durationString || durationString === "P") {
            years.value = 0; months.value = 0; weeks.value = 0; days.value = 0
            hours.value = 0; minutes.value = 0; seconds.value = 0
            durationIssue.value = null
            return
        }

        const match = durationString.match(
            /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/,
        )

        if (!match) {
            durationIssue.value = `Invalid ISO 8601 duration: ${durationString}`
            emit("update:modelValue", null)
            return
        }

        years.value = parseInt(match[1] ?? "0")
        months.value = parseInt(match[2] ?? "0")
        weeks.value = parseInt(match[3] ?? "0")
        days.value = parseInt(match[4] ?? "0")
        hours.value = parseInt(match[5] ?? "0")
        minutes.value = parseInt(match[6] ?? "0")
        seconds.value = parseInt(match[7] ?? "0")
        durationIssue.value = null
    }

    watch(years, updateDuration)
    watch(months, updateDuration)
    watch(weeks, updateDuration)
    watch(days, updateDuration)
    watch(hours, updateDuration)
    watch(minutes, updateDuration)
    watch(seconds, updateDuration)

    watch(() => props.modelValue, (val: string | null | undefined) => {
        if (val && val !== customDuration.value) {
            parseDuration(val)
        }
    })

    onMounted(() => {
        parseDuration(props.modelValue ?? "")
        updateDuration()
    })
</script>

<style lang="scss">
    .ks-duration-picker {
        &__fields {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
        }

        &__field {
            display: flex;
            flex-direction: column;
            align-items: center;
            width: 80px;
        }

        &__custom {
            margin-top: 0.5rem;
        }
    }
</style>
