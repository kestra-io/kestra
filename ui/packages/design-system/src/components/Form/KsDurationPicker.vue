<template>
    <div class="ks-duration-picker" v-bind="$attrs">
        <div class="ks-duration-picker__fields">
            <div class="ks-duration-picker__field">
                <label for="ks-duration-days">{{ $t('days') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-days"
                    v-model="days"
                    :disabled="disabled"
                    :min="0"
                    @change="updateDuration"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-hours">{{ $t('hours') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-hours"
                    v-model="hours"
                    :disabled="disabled"
                    :min="0"
                    @change="updateDuration"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-minutes">{{ $t('minutes') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-minutes"
                    v-model="minutes"
                    :disabled="disabled"
                    :min="0"
                    @change="updateDuration"
                />
            </div>
            <div class="ks-duration-picker__field">
                <label for="ks-duration-seconds">{{ $t('seconds') }}</label>
                <KsInputNumber
                    size="small"
                    controlsPosition="right"
                    id="ks-duration-seconds"
                    v-model="seconds"
                    :disabled="disabled"
                    :min="0"
                    @change="updateDuration"
                />
            </div>
        </div>
        <div class="ks-duration-picker__custom">
            <KsText size="small" :type="durationIssue ? 'danger': ''">
                {{ durationIssue ?? $t('input_custom_duration') }}
            </KsText>
            <KsInput type="text" id="ks-duration-custom" v-model="customDuration" @input="parseDuration" :disabled="disabled" :placeholder="$t('datepicker.custom duration')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | null
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: string | null]
    }>()

    const days = ref(0)
    const hours = ref(0)
    const minutes = ref(0)
    const seconds = ref(0)
    const customDuration = ref("")
    const durationIssue = ref<string | null>(null)

    const serializeDuration = (): string | null => {
        let duration = "P"
        if (days.value > 0) duration += `${days.value}D`

        if (hours.value > 0 || minutes.value > 0 || seconds.value > 0) {
            duration += "T"
            if (hours.value > 0) duration += `${hours.value}H`
            if (minutes.value > 0) duration += `${minutes.value}M`
            if (seconds.value > 0) duration += `${seconds.value}S`
        }

        return duration === "P" ? null : duration
    }

    /** Wired to unit @change, not unit watchers: programmatic unit writes from applyDuration must not rewrite the text being typed. */
    const updateDuration = () => {
        const finalDuration = serializeDuration()
        customDuration.value = finalDuration ?? ""
        durationIssue.value = null
        emit("update:modelValue", finalDuration)
    }

    const applyDuration = (durationString: string): boolean => {
        if (!durationString || durationString === "P") {
            days.value = 0
            hours.value = 0; minutes.value = 0; seconds.value = 0
            durationIssue.value = null
            return true
        }

        const match = durationString.match(
            /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/,
        )

        if (!match) {
            durationIssue.value = `Invalid ISO 8601 duration: ${durationString}`
            return false
        }

        days.value = parseInt(match[1] ?? "0")
        hours.value = parseInt(match[2] ?? "0")
        minutes.value = parseInt(match[3] ?? "0")
        seconds.value = parseInt(match[4] ?? "0")
        durationIssue.value = null
        return true
    }

    const parseDuration = (durationString: string) => {
        customDuration.value = durationString
        emit("update:modelValue", applyDuration(durationString) ? serializeDuration() : null)
    }

    watch(() => props.modelValue, (val: string | null | undefined) => {
        if (val && val !== customDuration.value) {
            customDuration.value = val
            applyDuration(val)
        }
    })

    onMounted(() => {
        applyDuration(props.modelValue ?? "")
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
