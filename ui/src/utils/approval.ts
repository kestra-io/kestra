import type {Execution, InputMetaData, ReviewDecision} from "../stores/executions"
import * as FlowUtils from "./flowUtils"
import * as ExecutionUtils from "./executionUtils"

export const APPROVAL_TYPE = "io.kestra.plugin.core.flow.Approval"

export interface ApprovalTask {
    id: string;
    type: string;
    inputs?: InputMetaData[];
    decisions?: {approve?: string; deny?: string};
    commentRequired?: string;
}

export interface ApprovalTaskRun {
    id: string;
    taskId: string;
    outputs?: Record<string, unknown>;
}

export function findApprovalTask(flow: unknown, taskId: string): ApprovalTask | undefined {
    const task = FlowUtils.findTaskById(flow, taskId)
    return task?.type === APPROVAL_TYPE ? task as unknown as ApprovalTask : undefined
}

export function findApprovalTaskRun(execution: Execution, flow: unknown, state: string): {taskRun: ApprovalTaskRun; task: ApprovalTask} | undefined {
    for (const taskRun of ExecutionUtils.findTaskRunsByState(execution, state)) {
        const task = findApprovalTask(flow, taskRun.taskId)
        if (task) {
            return {taskRun: taskRun as unknown as ApprovalTaskRun, task}
        }
    }
    return undefined
}

export function isCommentRequired(commentRequired: string | undefined, decision: ReviewDecision): boolean {
    switch (commentRequired) {
    case "ALWAYS":
        return true
    case "ON_APPROVE":
        return decision === "APPROVE"
    case "ON_DENY":
        return decision === "DENY"
    default:
        return false
    }
}

export function decisionLabel(label: string | undefined): string | undefined {
    return label === undefined || /\{\{|\{%/.test(label) ? undefined : label
}
