<template>
    <div class="execution-progress">
        <KsText size="small" class="progress-title">{{ $t("executionProgress.title") }}</KsText>
        <KsText size="small" class="progress-estimate">
            <template v-if="!averageDurationMs">
                {{ $t("executionProgress.noBaseline") }}
            </template>
            <template v-else-if="isPastEstimate">
                {{ $t("executionProgress.pastEstimate") }}
            </template>
            <template v-else>
                {{ $t("executionProgress.estimatedRemaining", {duration: remainingDisplay}) }}
            </template>
        </KsText>
        <KsProgress
            :percentage="progressPercent"
            :stroke-width="18"
            :showText="false"
            striped
            stripedFlow
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onBeforeUnmount} from "vue"

    import {durationUtils, KsProgress, KsText} from "@kestra-io/design-system"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

    import {type Execution} from "../../stores/executions"

    const props = defineProps<{
        execution: Execution
    }>()

    // null until the baseline request answers, and when the flow has no execution history to
    // estimate from — both cases render an empty bar.
    const averageDurationMs = ref<number | null>(null)

    // Timezone-independent: Date.now() counts milliseconds since the UTC epoch, as does parsing the
    // execution's ISO start date below, so the two can be subtracted whatever the browser timezone.
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/now
    const now = ref(Date.now())
    let ticker: ReturnType<typeof setInterval> | undefined

    const elapsedMs = computed(() => {
        const {startDate} = props.execution.state
        if (!startDate) return 0
        return Math.max(0, now.value - new Date(startDate).getTime())
    })

    const remainingMs = computed(() => {
        if (!averageDurationMs.value) return 0
        return Math.max(0, averageDurationMs.value - elapsedMs.value)
    })

    const remainingDisplay = computed(() => durationUtils.humanDuration(remainingMs.value / 1000))

    // Once elapsed passes the baseline there is nothing left to count down: without its own label the
    // estimate would read "0s remaining" for however much longer the execution actually runs.
    const isPastEstimate = computed(() => {
        const average = averageDurationMs.value
        return average !== null && elapsedMs.value >= average
    })

    // Capped below 100%: the bar must never claim completion while the execution is still running,
    // however far past its average duration it goes.
    const progressPercent = computed(() => {
        if (!averageDurationMs.value) return 0
        return Math.min(99, (elapsedMs.value / averageDurationMs.value) * 100)
    })

    onMounted(async () => {
        ticker = setInterval(() => {
            now.value = Date.now()
        }, 1000)

        try {
            const {avgDurationMs} = await ExecutionsAPI.executionAverageDuration({
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
            })
            // the backend omits the field entirely when there is no history (non_null serialization)
            averageDurationMs.value = avgDurationMs ?? null
        } catch {
            // no baseline: the bar stays empty rather than blocking the Gantt view
        }
    })

    onBeforeUnmount(() => {
        clearInterval(ticker)
    })
</script>

<style scoped lang="scss">
    /* Sits on --ks-bg-base, where the progress track (--ks-bg-hover) resolves to the same color and
       would be invisible; the surface background is what makes the unfilled part of the bar read. */
    .execution-progress {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-4);
        text-align: left;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
    }

    .progress-title,
    .progress-estimate {
        align-self: flex-start;
    }

    .progress-estimate {
        color: var(--ks-text-secondary);
    }
</style>
