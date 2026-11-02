import {describe, expect, it} from "vitest"
import {isCommentRequired, findApprovalTaskRun, decisionLabel, APPROVAL_TYPE} from "./approval"
import type {Execution} from "../stores/executions"

describe("isCommentRequired", () => {
    it.each([
        ["ALWAYS", "APPROVE", true],
        ["ALWAYS", "DENY", true],
        ["ON_DENY", "APPROVE", false],
        ["ON_DENY", "DENY", true],
        ["ON_APPROVE", "APPROVE", true],
        ["ON_APPROVE", "DENY", false],
        ["NEVER", "DENY", false],
        [undefined, "DENY", false],
    ] as const)("%s / %s -> %s", (mode, decision, expected) => {
        expect(isCommentRequired(mode, decision)).toBe(expected)
    })
})

describe("findApprovalTaskRun", () => {
    const flow = {tasks: [{id: "approval", type: APPROVAL_TYPE}, {id: "pause", type: "io.kestra.plugin.core.flow.Pause"}]}
    const execution = {
        taskRunList: [
            {id: "tr-pause", taskId: "pause", state: {current: "PAUSED"}},
            {id: "tr-approval", taskId: "approval", state: {current: "PAUSED"}},
        ],
    } as unknown as Execution

    it("skips a paused task that is not an Approval", () => {
        expect(findApprovalTaskRun(execution, flow, "PAUSED")?.taskRun.id).toBe("tr-approval")
    })

    it("returns nothing when no Approval is in that state", () => {
        expect(findApprovalTaskRun(execution, flow, "RUNNING")).toBeUndefined()
    })
})

describe("decisionLabel", () => {
    it.each([
        ["Continue", "Continue"],
        ["{{ inputs.label }}", undefined],
        ["{% if a %}x{% endif %}", undefined],
        [undefined, undefined],
    ])("%s -> %s", (label, expected) => {
        expect(decisionLabel(label)).toBe(expected)
    })
})
