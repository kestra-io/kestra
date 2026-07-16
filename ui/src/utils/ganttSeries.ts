export interface TaskBarInput {
    id: string
    parentTaskRunId?: string
    startTs: number
    stopTs: number
}

export interface TaskBarPercents {
    id: string
    start: number
    width: number
    parentEndPercent?: number
}

const DEFAULT_ALIGNMENT_EPSILON_MS = 200

/**
 * @param tasks               
 * @param executionStart      
 * @param executionDelta      
 * @param alignmentEpsilonMs 
 * @return each task's id paired with its start/width percentages and, if aligned, parentEndPercent
 */
export function computeTaskBarPercents(
    tasks: TaskBarInput[],
    executionStart: number,
    executionDelta: number,
    alignmentEpsilonMs = DEFAULT_ALIGNMENT_EPSILON_MS,
): TaskBarPercents[] {
    const stopTsById: Record<string, number> = {}
    const startPercentById: Record<string, number> = {}
    const widthById: Record<string, number> = {}
    const result: TaskBarPercents[] = []

    for (const task of tasks) {
        const start = ((task.startTs - executionStart) / executionDelta) * 100
        const width = ((task.stopTs - task.startTs) / executionDelta) * 100

        let parentEndPercent: number | undefined = undefined
        const parentStopTs = task.parentTaskRunId ? stopTsById[task.parentTaskRunId] : undefined

        if (parentStopTs !== undefined && parentStopTs - task.stopTs <= alignmentEpsilonMs) {
            parentEndPercent = startPercentById[task.parentTaskRunId as string] + widthById[task.parentTaskRunId as string]
        }

        stopTsById[task.id] = task.stopTs
        startPercentById[task.id] = start
        widthById[task.id] = width

        result.push({id: task.id, start, width, parentEndPercent})
    }

    return result
}