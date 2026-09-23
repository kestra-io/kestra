<template>
    <div style="flex:1">
        <KsProgress
            v-if="loopIterationCount > 0"
            :percentage="Math.round((consolidatedTerminalStates / loopIterationCount) * 1000) / 10"
            :strokeWidth="7"
            :radius="81"
            class="progress-bar"
        />

        <div class="pill-list">
            <KsButton 
                :tag="RouterLink" 
                v-for="segment in loopSegments" 
                :key="segment.state" 
                size="small"
                :to="{
                    // execution list filtered by Parent execution, Loop task and state
                    name: 'executions/list',
                    query: {
                        'filters[parentId][EQUALS]': executionId,
                        'filters[kind][EQUALS]': 'LOOP',
                        'filters[taskId][EQUALS]': taskId,
                        'filters[state][IN]': segment.state
                    }
                }"
            >
                <span :style="{backgroundColor: segment.color}" class="colored-dot"/>
                {{ segment.count }} {{ segment.state.toLowerCase().capitalize() }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State} from "@kestra-io/design-system"
    import {RouterLink} from "vue-router"

    // Color for each execution state, used to render the Loop task's per-state progress segments
    const loopStateColors = State.color()

    const props = defineProps<{
        executionId: string;
        currentTaskRunId: string;
        taskId: string;
        loopOutputsByTaskRunId: Record<string, { iterationCount: number; terminatedIterations?: Record<string, number>; runningIterations?: number }>;
    }>()

    const loopIterationCount = computed(() => {
        return props.loopOutputsByTaskRunId[props.currentTaskRunId]?.iterationCount ?? 0
    })

    const consolidatedTerminalStates = computed(() => {
        const terminatedIterations = props.loopOutputsByTaskRunId[props.currentTaskRunId]?.terminatedIterations ?? {}
        return Object.values(terminatedIterations).reduce((acc, count) => acc + count, 0)
    })

    // One colored segment per terminal state reached by the Loop's sub-executions.
    const loopSegments = computed(() => {
        const loopOutputs = props.loopOutputsByTaskRunId[props.currentTaskRunId] ?? {}
        const terminatedIterations = loopOutputs.terminatedIterations ?? {}
        const runningIterations = loopOutputs.runningIterations ?? 0
        const iterationCount = loopOutputs.iterationCount ?? 0

        const segments = Object.entries(terminatedIterations).map(([state, count]) => ({
            state,
            count,
            color: loopStateColors[state],
            tooltip: `${count} ${state}`,
        }))

        if (runningIterations > 0) {
            segments.push({
                state: "RUNNING",
                count: runningIterations,
                color: loopStateColors["RUNNING"],
                tooltip: `${runningIterations} RUNNING`,
            })
        }

        const createdIterations = iterationCount - consolidatedTerminalStates.value - runningIterations
        
        if (createdIterations > 0) {
            segments.push({
                state: "CREATED",
                count: createdIterations,
                color: loopStateColors["CREATED"],
                tooltip: `${createdIterations} CREATED`,
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
