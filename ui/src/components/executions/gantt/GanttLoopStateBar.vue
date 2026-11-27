<template>
    <LoopStateBar v-if="bar" :counts="bar.counts" :total="bar.total" :running="bar.running" />
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {LoopStateBar, type StateCounts} from "@kestra-io/topology"
    import {State} from "@kestra-io/design-system"
    import {useGanttTree, type TaskRunNode} from "./ganttTree"
    import {useLoopRefresh} from "./useLoopRefresh"

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

    useLoopRefresh(() => props.node)

    watch(() => [props.node.taskRun.id, state.value], () => tree.loadLoopOutputs(props.node), {immediate: true})
</script>
