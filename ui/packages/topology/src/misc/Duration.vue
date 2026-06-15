<template>
    <KsTooltip :key="lastStep?.date?.valueOf() ?? $t('no_history')">
        <template #content>
            <span
                v-for="(history, index) in filteredHistories"
                :key="'tt-' + index"
                class="ks-duration-tt"
            >
                <span class="ks-duration-tt-square" :class="squareClass(history.state)" />
                <strong>{{ history.state }}: </strong>{{ Utils.dateFilter(history.date.toISOString(), "iso") }} <br>
            </span>
        </template>
        <template #default>
            <span class="ks-duration-value" v-html="duration" />
        </template>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import moment, {type Moment} from "moment"
    import {State, KsTooltip} from "@kestra-io/design-system"
    import * as Utils from "../utils/utils"

    const props = withDefaults(defineProps<{
        histories?: {
            date: string | number | Moment;
            state: string;
        }[];
        interval?: number;
    }>(), {
        histories: undefined,
        interval: 100,
    })

    const normalizedHistories = computed(() =>
        (props.histories ?? []).map((h) => ({
            date: moment(h.date),
            state: h.state,
        })),
    )

    watch(
        normalizedHistories,
        (newValue, oldValue) => {
            if (newValue?.[0]?.date?.valueOf() !== oldValue?.[0]?.date?.valueOf()) {
                paint()
            }
        },
    )

    const duration = ref("")
    const refreshHandler = ref()

    onMounted(() => {
        paint()
    })

    const start = computed(() => {
        return normalizedHistories.value?.[0]?.date?.valueOf() ?? null
    })

    const lastStep = computed(() => {
        return normalizedHistories.value.length ? normalizedHistories.value[normalizedHistories.value.length - 1] : undefined
    })

    const filteredHistories = computed(() => {
        return normalizedHistories.value.filter((h) => h.date.isValid() && h.date && h.state)
    })

    function paint() {
        computeDuration()
        if (!refreshHandler.value) {
            refreshHandler.value = setInterval(() => {
                computeDuration()
                if (lastStep.value && !State.isRunning(lastStep.value.state)) {
                    cancel()
                }
            }, props.interval)
        }
    }

    function cancel() {
        if (refreshHandler.value) {
            clearInterval(refreshHandler.value)
            refreshHandler.value = undefined
        }
    }

    function delta() {
        const startValue = start.value
        if (startValue === null) return 0
        return Math.max(0, stop() - startValue)
    }

    function stop() {
        if (!lastStep.value || State.isRunning(lastStep.value.state)) {
            return +new Date()
        }
        return lastStep.value.date.valueOf()
    }

    function computeDuration() {
        if (filteredHistories.value.length === 0) {
            duration.value = "&nbsp;"
            return
        }

        const human = Utils.humanDuration(delta() / 1000, {
            maxDecimalPoints: 2,
            units: ["h", "m", "s"],
        })

        const isBareSeconds = human.endsWith("s") && !human.endsWith("ms") && !human.includes(".")
        duration.value = isBareSeconds ? `${human.slice(0, -1)}.00s` : human
    }

    function squareClass(state: string) {
        let statusVarname = state.toLowerCase()

        // Minor hack to reuse created color for submitted status.
        // See https://github.com/kestra-io/kestra/issues/14876 for more details.
        if (statusVarname === "submitted") statusVarname = "created"

        return "ks-duration-tt-square-" + statusVarname
    }

    onBeforeUnmount(() => {
        cancel()
    })
</script>

<style scoped>
    .ks-duration-value {
        font-variant-numeric: tabular-nums;
    }
</style>

