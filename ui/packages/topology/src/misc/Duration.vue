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
            <span v-html="duration" />
        </template>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
    import {type Moment} from "moment"
    import {State, KsTooltip} from "@kestra-io/design-system"
    import * as Utils from "../utils/utils"

    const props = defineProps<{
        histories: {
            date: Moment;
            state: string;
        }[];
    }>()

    watch(
        () => props.histories,
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
        return props.histories?.[0]?.date?.valueOf() ?? null
    })

    const lastStep = computed(() => {
        return props.histories?.length ? props.histories[props.histories.length - 1] : undefined
    })

    const filteredHistories = computed(() => {
        return props.histories.filter((h) => h.date.isValid() && h.date && h.state)
    })

    function paint() {
        if (!refreshHandler.value) {
            refreshHandler.value = setInterval(() => {
                computeDuration()
                if (lastStep.value && !State.isRunning(lastStep.value.state)) {
                    cancel()
                }
            }, 10)
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
        duration.value =
            filteredHistories.value.length === 0
                ? "&nbsp;"
                : Utils.humanDuration(delta() / 1000, {
                    maxDecimalPoints: 2,
                    units: ["h", "m", "s"],
                })
    }

    function squareClass(state: string) {
        return "ks-duration-tt-square-" + state.toLowerCase()
    }

    onBeforeUnmount(() => {
        cancel()
    })
</script>

