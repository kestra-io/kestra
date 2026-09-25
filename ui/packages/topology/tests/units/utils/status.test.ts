import {describe, expect, it} from "vitest"
import {computeAggregateState, pickWorstState} from "../../../src/utils/status"

describe("pickWorstState", () => {
    it("should return undefined when given no states", () => {
        expect(pickWorstState([])).toBeUndefined()
    })

    it("should return the single state when only one is given", () => {
        expect(pickWorstState(["SUCCESS"])).toBe("SUCCESS")
    })

    it("should rank a single failure over any number of successes", () => {
        expect(pickWorstState(["SUCCESS", "SUCCESS", "FAILED", "SUCCESS"])).toBe("FAILED")
    })

    it("should rank running over success while an execution is still in flight", () => {
        expect(pickWorstState(["SUCCESS", "RUNNING"])).toBe("RUNNING")
    })
})

describe("computeAggregateState (lane-header aggregate state)", () => {
    const runsOf = (byTaskId: Record<string, string>) =>
        Object.entries(byTaskId).map(([taskId, state]) => ({taskId, state: {current: state}}))

    it("should report the shared success state when every child succeeded", () => {
        const childTaskIds = ["branch_a", "branch_b", "branch_c"]
        const taskRunList = runsOf({branch_a: "SUCCESS", branch_b: "SUCCESS", branch_c: "SUCCESS"})

        expect(computeAggregateState(childTaskIds, taskRunList)).toBe("SUCCESS")
    })

    it("should surface a partial failure over the children that still succeeded", () => {
        const childTaskIds = ["branch_a", "branch_b", "branch_c"]
        const taskRunList = runsOf({branch_a: "SUCCESS", branch_b: "FAILED", branch_c: "SUCCESS"})

        expect(computeAggregateState(childTaskIds, taskRunList)).toBe("FAILED")
    })

    it("should report undefined (none run) when no child has a task run yet", () => {
        const childTaskIds = ["branch_a", "branch_b"]

        expect(computeAggregateState(childTaskIds, [])).toBeUndefined()
    })

    it("should ignore a child with no task run instead of treating it as a failure", () => {
        // e.g. an `If`'s untaken branch: present in childTaskIds, never ran.
        const childTaskIds = ["then_branch", "else_branch"]
        const taskRunList = runsOf({then_branch: "SUCCESS"})

        expect(computeAggregateState(childTaskIds, taskRunList)).toBe("SUCCESS")
    })
})
