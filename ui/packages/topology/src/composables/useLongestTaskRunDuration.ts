import {computed, onScopeDispose, ref, watch, type Ref} from "vue"
import {State} from "@kestra-io/design-system"
import {computeLongestTaskRunDuration, toMillis, type DurationHistoryEntry, type TaskRunLike} from "../misc/durationBreakdown"

function latestState(histories: DurationHistoryEntry[]): string | undefined {
    let latest: DurationHistoryEntry | undefined
    for (const history of histories) {
        if (!latest || toMillis(history.date) >= toMillis(latest.date)) latest = history
    }
    return latest?.state
}

function isStillRunning(taskRun: TaskRunLike): boolean {
    const histories = taskRun.state?.histories
    if (!histories?.length) return false
    const state = latestState(histories)
    return Boolean(state && State.isRunning(state))
}

export function useLongestTaskRunDuration(taskRuns: Ref<TaskRunLike[]>, interval = 500) {
    const now = ref(Date.now())
    let ticker: ReturnType<typeof setInterval> | undefined

    const runningTaskRuns = computed(() => taskRuns.value.filter(isStillRunning))
    const longestTerminal = computed(() =>
        computeLongestTaskRunDuration(taskRuns.value.filter((taskRun) => !isStillRunning(taskRun))),
    )

    function stop() {
        if (ticker) {
            clearInterval(ticker)
            ticker = undefined
        }
    }

    watch(() => runningTaskRuns.value.length > 0, (running) => {
        if (!running) return stop()
        now.value = Date.now()
        ticker ??= setInterval(() => {
            now.value = Date.now()
        }, interval)
    }, {immediate: true})

    onScopeDispose(stop)

    return computed(() => Math.max(longestTerminal.value, computeLongestTaskRunDuration(runningTaskRuns.value, now.value)))
}
