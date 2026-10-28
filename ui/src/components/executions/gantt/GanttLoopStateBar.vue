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

    const bar = computed(() => {
        const outputs = tree.loopOutputs(props.node.taskRun.id)?.outputs
        const total = outputs?.iterationCount
        const terminated = outputs?.terminatedIterations as StateCounts | undefined
        const running = outputs?.runningIterations as number | undefined
        if (typeof total !== "number" || (terminated === undefined && running === undefined)) return undefined
        return {counts: terminated, total, running: terminal.value ? 0 : running}
    })

    const hasNoCounts = computed(() => {
        const entry = tree.loopOutputs(props.node.taskRun.id)
        return entry !== undefined && !entry.failed && bar.value === undefined
    })
    const polling = computed(() => !terminal.value && !hasNoCounts.value)

    let timer: ReturnType<typeof setInterval> | undefined

    function stopPolling() {
        clearInterval(timer)
        timer = undefined
    }

    watch(polling, (active) => {
        stopPolling()
        if (active) timer = setInterval(() => tree.loadLoopOutputs(props.node), LOOP_OUTPUTS_REFRESH_MS / 2)
    }, {immediate: true})

    onBeforeUnmount(stopPolling)

    watch(() => [props.node.taskRun.id, state.value], () => tree.loadLoopOutputs(props.node), {immediate: true})
</script>
