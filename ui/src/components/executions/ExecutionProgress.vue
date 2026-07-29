<template>
    <div class="execution-progress">
        <div class="progress-heading">
            <KsText size="small">{{ $t("executionProgress.title") }}</KsText>
            <KsText size="small" class="progress-estimate">
                <template v-if="averageDurationMs">
                    {{ $t("executionProgress.estimatedRemaining", {duration: remainingDisplay}) }}
                </template>
                <template v-else>
                    {{ $t("executionProgress.noBaseline") }}
                </template>
            </KsText>
        </div>
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

    import {useExecutionsStore, type Execution} from "../../stores/executions"

    const props = defineProps<{
        execution: Execution
    }>()

    const executionsStore = useExecutionsStore()

    // null until the baseline request answers, and when the flow has no execution history to
    // estimate from — both cases render an empty bar.
    const averageDurationMs = ref<number | null>(null)
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
            const {avgDurationMs} = await executionsStore.loadFlowAverageDuration({
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

    .progress-heading {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--ks-spacing-4);
    }

    .progress-estimate {
        color: var(--ks-text-secondary);
    }
</style>
