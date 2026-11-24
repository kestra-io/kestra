import {computed, onBeforeUnmount, watch} from "vue"
import {State} from "@kestra-io/design-system"
import type {LoopIterationRow} from "../../../composables/useLoopIterations"
import {hasRunningIterations} from "../../../utils/loopIterations"
import {LOOP_OUTPUTS_REFRESH_MS, useGanttTree, type TaskRunNode} from "./ganttTree"

export function useLoopRefresh(
    node: () => TaskRunNode,
    options: {refresh?: () => unknown; rows?: () => LoopIterationRow[]} = {},
) {
    const tree = useGanttTree()

    // A row still shown as running keeps the refresh going, so the last one carries the final states.
    const active = computed(() => {
        const {taskRun} = node()
        return hasRunningIterations(taskRun.state.current, tree.loopOutputs(taskRun.id)?.outputs)
            || (options.rows?.() ?? []).some(row => !State.isTerminated(row.state.current))
    })

    let timer: ReturnType<typeof setInterval> | undefined

    function stop() {
        clearInterval(timer)
        timer = undefined
    }

    watch(active, (isActive) => {
        stop()
        if (!isActive) return
        timer = setInterval(() => {
            tree.loadLoopOutputs(node())
            options.refresh?.()
        }, LOOP_OUTPUTS_REFRESH_MS / 2)
    }, {immediate: true})

    onBeforeUnmount(stop)
}
