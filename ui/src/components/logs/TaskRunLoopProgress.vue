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
            <KsButton
                v-for="segment in loopSegments"
                :key="segment.key"
                :tag="segment.filterStates ? RouterLink : 'div'"
                size="small"
                :data-state="segment.key"
                :class="{'muted-pill': !segment.filterStates}"
                :to="segment.filterStates ? {
                    name: 'executions/list',
                    query: {
                        'filters[parentId][EQUALS]': executionId,
                        'filters[kind][EQUALS]': 'LOOP',
                        'filters[taskId][EQUALS]': taskId,
                        'filters[state][IN]': segment.filterStates.join(',')
                    }
                } : undefined"
            >
                <span :style="{backgroundColor: segment.color}" class="colored-dot" />
                {{ segment.count }} {{ segment.label }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State} from "@kestra-io/design-system"
    import {RouterLink} from "vue-router"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n({useScope: "global"})
    const loopStateColors = State.color()

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
        loopTaskState?: string;
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

    function formatPercentage(): string {
        if (loopIterationCount.value <= 0) return "0.0%"
        if (consolidatedTerminalStates.value >= loopIterationCount.value) return "100.0%"
        return `${(Math.floor(consolidatedTerminalStates.value * 1000 / loopIterationCount.value) / 10).toFixed(1)}%`
    }

    const loopSegments = computed<LoopSegment[]>(() => {
        const outputs = props.loopOutputsByTaskRunId[props.currentTaskRunId]
        const terminatedIterations = outputs?.terminatedIterations ?? {}
        const inFlightCount = Math.max(0, Math.min(outputs?.runningIterations ?? 0, loopIterationCount.value - consolidatedTerminalStates.value))

        const allStates = State.arrayAllStates().map(s => s.name)

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
                const ai = allStates.indexOf(a.key)
                const bi = allStates.indexOf(b.key)
                return (ai === -1 ? allStates.length : ai) - (bi === -1 ? allStates.length : bi)
            })

        const segments: LoopSegment[] = [...terminalSegments]

        const isLoopTerminated = !!(props.loopTaskState && State.isTerminated(props.loopTaskState))

        if (!isLoopTerminated && inFlightCount > 0) {
            segments.push({
                key: "IN_FLIGHT",
                label: t("in flight"),
                count: inFlightCount,
                color: loopStateColors.RUNNING,
                filterStates: allStates.filter(s => !State.isTerminated(s)),
            })
        }

        const activeInFlightCount = isLoopTerminated ? 0 : inFlightCount
        const notStartedCount = Math.max(0, loopIterationCount.value - consolidatedTerminalStates.value - activeInFlightCount)
        if (notStartedCount > 0) {
            segments.push({
                key: "NOT_STARTED",
                label: t("not started"),
                count: notStartedCount,
                color: "var(--ks-border-default)",
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

  .muted-pill {
    pointer-events: none;
    border-color: var(--ks-border-subtle);
  }
</style>
