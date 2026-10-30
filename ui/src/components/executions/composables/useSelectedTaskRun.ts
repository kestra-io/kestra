import {computed} from "vue"
import {State} from "@kestra-io/design-system"
import type {TaskRun, TaskRunAttempt} from "@kestra-io/kestra-sdk"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"

interface SelectedTaskRunSource {
    execution: () => Execution
    taskRun: () => ExecutionTaskRun
    taskRuns: () => ExecutionTaskRun[] | undefined
    attemptIndex?: () => number | undefined
    forcedAttemptNumber?: () => number | undefined
}

export function useSelectedTaskRun(source: SelectedTaskRunSource) {
    const executionsStore = useExecutionsStore()

    const currentTaskRuns = computed(() =>
        (source.taskRuns() ?? []).filter((run) => run.taskId === source.taskRun()?.taskId),
    )

    const selectionKey = computed(() => `${source.execution().id}|${source.taskRun()?.taskId}`)

    const selectedTaskRunId = computed({
        get: () => executionsStore.taskRunSelections.get(selectionKey.value) || source.taskRun()?.id || currentTaskRuns.value[0]?.id,
        set: (id: string) => executionsStore.taskRunSelections.set(selectionKey.value, id),
    })

    const currentTaskRun = computed(() =>
        currentTaskRuns.value.find((run) => run.id === selectedTaskRunId.value) || source.taskRun(),
    )

    const currentTaskRunWithExecution = computed<TaskRun>(() => ({
        ...currentTaskRun.value,
        executionId: source.execution().id,
        namespace: source.execution().namespace,
        flowId: source.execution().flowId.toString(),
    } satisfies TaskRun))

    function attempts(taskRun: ExecutionTaskRun): TaskRunAttempt[] {
        const forced = source.forcedAttemptNumber?.()
        if (source.execution().state.current === State.RUNNING || forced === undefined) {
            return taskRun.attempts ?? [{state: taskRun.state}]
        }
        return taskRun.attempts ? [taskRun.attempts[forced]] : []
    }

    const currentAttemptIndex = computed(() => {
        const attemptCount = attempts(currentTaskRun.value).length
        return Math.min(source.attemptIndex?.() ?? 0, Math.max(0, attemptCount - 1))
    })

    const selectedAttempt = computed(() => attempts(currentTaskRun.value)[currentAttemptIndex.value])

    return {
        currentTaskRuns,
        selectedTaskRunId,
        currentTaskRun,
        currentTaskRunWithExecution,
        currentAttemptIndex,
        selectedAttempt,
    }
}
