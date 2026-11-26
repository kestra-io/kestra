<template>
    <div v-if="isVisible" class="execution-progress" data-test="execution-progress">
        <div class="progress-header">
            <KsText size="small" class="progress-title" data-test="execution-progress-title">
                {{ $t("executionProgress.title") }}
            </KsText>
            <KsText size="small" class="progress-estimate" data-test="execution-progress-estimate">
                <template v-if="isPastEstimate">
                    {{ $t("executionProgress.pastEstimate") }}
                </template>
                <template v-else>
                    {{ $t("executionProgress.estimatedRemaining", {duration: remainingDisplay}) }}
                </template>
            </KsText>
        </div>
        <KsProgress
            :percentage="progressPercent"
            :stroke-width="8"
            :showText="false"
            striped
            stripedFlow
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onBeforeUnmount, watch} from "vue"

    import {durationUtils, KsProgress, KsText, State} from "@kestra-io/design-system"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

    import {type Execution} from "../../stores/executions"

    const props = defineProps<{
        execution: Execution
    }>()

    // null until the baseline request answers, and when the flow has no execution history to
    // estimate from — both cases skip rendering the progress bar.
    const averageDurationMs = ref<number | null>(null)

    const isRunning = computed(() => props.execution.state?.current === State.RUNNING)
    const isVisible = computed(() => isRunning.value && averageDurationMs.value !== null)

    // Timezone-independent: Date.now() counts milliseconds since the UTC epoch, as does parsing the
    // execution's ISO start date below, so the two can be subtracted whatever the browser timezone.
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/now
    const now = ref(Date.now())
    let ticker: ReturnType<typeof setInterval> | undefined

    const elapsedMs = computed(() => {
        const startDate = props.execution.state?.startDate
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

    let retryTimeout: ReturnType<typeof setTimeout> | undefined

    const fetchAverageDuration = async () => {
        try {
            const {avgDurationMs} = await ExecutionsAPI.executionAverageDuration({
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
            })
            // the backend omits the field entirely when there is no history (non_null serialization)
            averageDurationMs.value = avgDurationMs ?? null
        } catch {
            if (isRunning.value && !retryTimeout) {
                retryTimeout = setTimeout(() => {
                    retryTimeout = undefined
                    if (isRunning.value && averageDurationMs.value === null) {
                        fetchAverageDuration()
                    }
                }, 10_000)
            }
        }
    }

    watch(
        isRunning,
        (running) => {
            if (running) {
                if (!ticker) {
                    ticker = setInterval(() => {
                        now.value = Date.now()
                    }, 1000)
                }
                if (averageDurationMs.value === null) {
                    fetchAverageDuration()
                }
            } else {
                if (ticker) {
                    clearInterval(ticker)
                    ticker = undefined
                }
                if (retryTimeout) {
                    clearTimeout(retryTimeout)
                    retryTimeout = undefined
                }
            }
        },
        {immediate: true},
    )

    onBeforeUnmount(() => {
        if (ticker) {
            clearInterval(ticker)
        }
        if (retryTimeout) {
            clearTimeout(retryTimeout)
        }
    })
</script>

<style scoped>
    .execution-progress {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-5);
        text-align: left;
        border-top: 1px solid var(--ks-border-default);
    }

    .progress-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .progress-estimate {
        color: var(--ks-text-secondary);
        white-space: nowrap;
    }
</style>
