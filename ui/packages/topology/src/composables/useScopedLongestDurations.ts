import {computed, effectScope, onScopeDispose, ref, shallowRef, watch, type ComputedRef, type EffectScope, type ShallowRef} from "vue"
import type {TaskRunLike} from "../misc/durationBreakdown"
import {useLongestTaskRunDuration} from "./useLongestTaskRunDuration"

interface ScopedExecution {
    id?: string;
    taskRunList?: TaskRunLike[];
}

interface Tracked {
    scope: EffectScope;
    taskRuns: ShallowRef<TaskRunLike[]>;
    duration: ComputedRef<number>;
}

export function useScopedLongestDurations(executions: () => ScopedExecution[]) {
    const tracked = new Map<string, Tracked>()
    const version = ref(0)

    function track(execution: ScopedExecution & {id: string}) {
        const existing = tracked.get(execution.id)
        if (existing) {
            existing.taskRuns.value = execution.taskRunList ?? []
            return
        }
        const scope = effectScope()
        const taskRuns = shallowRef(execution.taskRunList ?? [])
        const duration = scope.run(() => useLongestTaskRunDuration(taskRuns))!
        tracked.set(execution.id, {scope, taskRuns, duration})
    }

    watch(executions, (current) => {
        const live = new Set<string>()
        for (const execution of current) {
            if (!execution.id) continue
            live.add(execution.id)
            track({...execution, id: execution.id})
        }
        for (const [id, entry] of tracked) {
            if (live.has(id)) continue
            entry.scope.stop()
            tracked.delete(id)
        }
        version.value++
    }, {immediate: true})

    onScopeDispose(() => tracked.forEach((entry) => entry.scope.stop()))

    return computed<Record<string, number>>(() => {
        void version.value
        return Object.fromEntries([...tracked].map(([id, entry]) => [id, entry.duration.value]))
    })
}
