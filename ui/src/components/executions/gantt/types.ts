export interface FailureTaskRun {
    id: string
    taskId: string
    parentTaskRunId?: string
    value?: string
    attempts?: unknown[]
    state: {
        current: string
        histories: Array<{state: string; date: string}>
    }
}

export type StructuralRelation = "focused" | "parent" | "sibling" | "child"

export interface StructuralNode {
    taskRun: FailureTaskRun
    relation: StructuralRelation
}
