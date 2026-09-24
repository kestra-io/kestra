<template>
    <div style="flex:1">
        <KsProgress
            v-if="loopIterationCount > 0"
            :percentage="completedPercentage"
            :format="formatPercentage"
            :strokeWidth="7"
            :radius="81"
            class="progress-bar"
        />

        <div class="pill-list">
            <template v-for="segment in loopSegments" :key="segment.key">
                <KsButton
                    v-if="segment.filterStates"
                    :tag="RouterLink"
                    size="small"
                    :to="{
                        name: 'executions/list',
                        query: {
                            'filters[parentId][EQUALS]': executionId,
                            'filters[kind][EQUALS]': 'LOOP',
                            'filters[taskId][EQUALS]': taskId,
                            'filters[state][IN]': segment.filterStates.join(',')
                        }
                    }"
                >
                    <span :style="{backgroundColor: segment.color}" class="colored-dot" />
                    {{ segment.count }} {{ segment.label }}
                </KsButton>
                <KsButton v-else size="small" disabled>
                    <span :style="{backgroundColor: segment.color}" class="colored-dot" />
                    {{ segment.count }} {{ segment.label }}
                </KsButton>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State} from "@kestra-io/design-system"
    import {RouterLink} from "vue-router"

    const loopStateColors = State.color()

    const IN_FLIGHT_FILTER_STATES = ["CREATED", "SUBMITTED", "RESTARTED", "RUNNING", "KILLING", "PAUSED", "QUEUED", "RETRYING", "BREAKPOINT"]

    const TERMINAL_STATE_ORDER = ["SUCCESS", "WARNING", "FAILED", "KILLED", "CANCELLED", "RETRIED", "SKIPPED"]

    type LoopSegment = {
        key: string;
        label: string;
        count: number;
        color: string;
        filterStates?: string[];
    }

    const props = defineProps<{
        executionId: string;
        currentTaskRunId: string;
        taskId: string;
        loopOutputsByTaskRunId: Record<string, {iterationCount: number; terminatedIterations?: Record<string, number>; runningIterations?: number}>;
    }>()

    const loopIterationCount = computed(() => {
        return props.loopOutputsByTaskRunId[props.currentTaskRunId]?.iterationCount ?? 0
    })

    const consolidatedTerminalStates = computed(() => {
        const terminatedIterations = props.loopOutputsByTaskRunId[props.currentTaskRunId]?.terminatedIterations ?? {}
        return Object.values(terminatedIterations).reduce((acc, count) => acc + count, 0)
    })

    const completedPercentage = computed(() => {
        if (loopIterationCount.value <= 0) return 0
        return Math.min(100, consolidatedTerminalStates.value / loopIterationCount.value * 100)
    })

    function formatPercentage(percentage: number): string {
        return `${percentage.toFixed(1)}%`
    }

    const loopSegments = computed<LoopSegment[]>(() => {
        const outputs = props.loopOutputsByTaskRunId[props.currentTaskRunId]
        const terminatedIterations = outputs?.terminatedIterations ?? {}
        const inFlightCount = Math.max(0, Math.min(outputs?.runningIterations ?? 0, loopIterationCount.value - consolidatedTerminalStates.value))

        const terminalSegments: LoopSegment[] = Object.entries(terminatedIterations)
            .filter(([, count]) => count > 0)
            .map(([state, count]) => ({
                key: state,
                label: state.toLowerCase().capitalize(),
                count,
                color: loopStateColors[state],
                filterStates: [state],
            }))
            .sort((a, b) => {
                const ai = TERMINAL_STATE_ORDER.indexOf(a.key)
                const bi = TERMINAL_STATE_ORDER.indexOf(b.key)
                return (ai === -1 ? TERMINAL_STATE_ORDER.length : ai) - (bi === -1 ? TERMINAL_STATE_ORDER.length : bi)
            })

        const segments: LoopSegment[] = [...terminalSegments]

        if (inFlightCount > 0) {
            segments.push({
                key: "IN_FLIGHT",
                label: "In flight",
                count: inFlightCount,
                color: loopStateColors.RUNNING,
                filterStates: IN_FLIGHT_FILTER_STATES,
            })
        }

        const notStartedCount = Math.max(0, loopIterationCount.value - consolidatedTerminalStates.value - inFlightCount)
        if (notStartedCount > 0) {
            segments.push({
                key: "NOT_STARTED",
                label: "Not started",
                count: notStartedCount,
                color: "var(--ks-border-primary)",
            })
        }

        return segments
    })
</script>

<style lang="scss" scoped>
  .progress-bar {
    margin-block: .3rem;
    flex: 1;

    :deep(.kel-progress__text) {
      font-size: var(--ks-font-size-sm) !important;
      color: var(--ks-text-secondary);
    }
  }

  .pill-list{
    margin-top: var(--ks-spacing-3);
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .colored-dot{
    display: inline-block;
    width: 0.5rem;
    height: 0.5rem;
    border-radius: 50%;
    margin-right: 0.5rem;
  }
</style>
