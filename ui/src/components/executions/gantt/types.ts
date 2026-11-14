import type {ExecutionTaskRun} from "../executionTaskRun"

export type StructuralRelation = "focused" | "parent" | "sibling" | "child"

export interface StructuralNode {
    taskRun: ExecutionTaskRun
    relation: StructuralRelation
}
