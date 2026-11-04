<template>
    <LoopStateBar v-if="bar" :counts="bar.counts" :total="bar.total" :running="bar.running" />
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, watch} from "vue"
    import {LoopStateBar, type StateCounts} from "@kestra-io/topology"
    import {State} from "@kestra-io/design-system"
    import {LOOP_OUTPUTS_REFRESH_MS, useGanttTree, type TaskRunNode} from "./ganttTree"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const tree = useGanttTree()
    const state = computed(() => props.node.taskRun.state.current)
    const terminal = computed(() => State.isTerminated(state.value))
    const outputs = computed(() => tree.loopOutputs(props.node.taskRun.id)?.outputs)

    const bar = computed(() => {
        const total = outputs.value?.iterationCount
        const terminated = outputs.value?.terminatedIterations as StateCounts | undefined
        const running = outputs.value?.runningIterations as number | undefined
        if (typeof total !== "number" || (terminated === undefined && running === undefined)) return undefined
        return {counts: terminated, total, running: terminal.value ? 0 : running}
    })

    const everyIterationTerminated = computed(() => {
        const total = outputs.value?.iterationCount
        const terminated = outputs.value?.terminatedIterations as StateCounts | undefined
        if (typeof total !== "number" || terminated === undefined || outputs.value?.runningIterations !== 0) return false
        return Object.values(terminated).reduce<number>((sum, count) => sum + (count ?? 0), 0) >= total
    })

    let timer: ReturnType<typeof setInterval> | undefined

    function stopPolling() {
        clearInterval(timer)
        timer = undefined
    }

    watch(() => !terminal.value && !everyIterationTerminated.value, (active) => {
        stopPolling()
        if (active) timer = setInterval(() => tree.loadLoopOutputs(props.node), LOOP_OUTPUTS_REFRESH_MS / 2)
    }, {immediate: true})

    onBeforeUnmount(stopPolling)

    watch(() => [props.node.taskRun.id, state.value], () => tree.loadLoopOutputs(props.node), {immediate: true})
</script>
