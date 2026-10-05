import {computed, onBeforeUnmount, ref, watch, type Ref} from "vue"
import {computeDurationBreakdown, computeLongestTaskRunDuration, type TaskRunLike} from "../misc/durationBreakdown"

export function useLongestTaskRunDuration(taskRuns: Ref<TaskRunLike[]>, interval = 500) {
    const now = ref(Date.now())
    let ticker: ReturnType<typeof setInterval> | undefined

    const anyRunning = computed(() =>
        taskRuns.value.some((taskRun) => {
            const histories = taskRun.state?.histories
            return Boolean(histories?.length) && computeDurationBreakdown(histories!).isRunning
        }),
    )

    function stop() {
        if (ticker) {
            clearInterval(ticker)
            ticker = undefined
        }
    }

    watch(anyRunning, (running) => {
        if (!running) return stop()
        now.value = Date.now()
        ticker ??= setInterval(() => {
            now.value = Date.now()
        }, interval)
    }, {immediate: true})

    onBeforeUnmount(stop)

    return computed(() => computeLongestTaskRunDuration(taskRuns.value, now.value))
}
