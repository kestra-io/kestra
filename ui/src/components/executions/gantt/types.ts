export interface FailureStateHistoryEntry {
    state: string
    date: string
}

export interface FailureAttempt {
    state: {
        current: string
        histories: FailureStateHistoryEntry[]
    }
    workerId?: string
}

export interface FailureTaskRun {
    id: string
    taskId: string
    parentTaskRunId?: string
    value?: string
    iteration?: number
    attempts?: FailureAttempt[]
    state: {
        current: string
        histories: FailureStateHistoryEntry[]
    }
}

export type StructuralRelation = "focused" | "parent" | "sibling" | "child"

export interface StructuralNode {
    taskRun: FailureTaskRun
    relation: StructuralRelation
}
