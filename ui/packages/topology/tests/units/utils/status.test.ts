import {describe, expect, it, test} from "vitest"
import {computeAggregateState, getStatusStyle, pickWorstState} from "../../../src/utils/status"

describe("getStatusStyle", () => {
    test.each([
        ["success", "--ks-text-success", "var(--ks-topology-bg-success)", "var(--ks-border-success)"],
        ["failed", "--ks-text-error", "var(--ks-topology-bg-errors)", "var(--ks-topology-border-errors)"],
        ["killed", "--ks-text-error", "var(--ks-topology-bg-errors)", "var(--ks-topology-border-errors)"],
        ["running", "--ks-status-running", "var(--ks-topology-bg-running)", "var(--ks-topology-border-running)"],
        ["killing", "--ks-status-pending", "var(--ks-topology-bg-killing)", "var(--ks-topology-border-killing)"],
        ["skipped", "--ks-status-neutral", undefined, undefined],
        ["cancelled", "--ks-status-neutral", "var(--ks-topology-bg-cancelled)", "var(--ks-topology-border-cancelled)"],
    ])("returns the correct style for %s", (state, textVar, bg, border) => {
        const style = getStatusStyle(state)

        expect(style?.textVar).toBe(textVar)
        expect(style?.bg).toBe(bg)
        expect(style?.border).toBe(border)
    })

    test("matches states case-insensitively", () => {
        expect(getStatusStyle("SUCCESS")).toBe(getStatusStyle("success"))
        expect(getStatusStyle("Skipped")).toBe(getStatusStyle("skipped"))
    })

    test("returns the neutral style for an unknown state", () => {
        const style = getStatusStyle("paused")

        expect(style?.textVar).toBe("--ks-status-neutral")
        expect(style?.bg).toBeUndefined()
        expect(style?.border).toBeUndefined()
    })

    test.each([undefined, null, ""])("returns undefined for empty state %s", (state) => {
        expect(getStatusStyle(state)).toBeUndefined()
    })

    test("adds dimIcon and label only to skipped", () => {
        expect(getStatusStyle("skipped")?.dimIcon).toBe(true)
        expect(getStatusStyle("skipped")?.label).toBe("skipped")

        for (const state of ["success", "failed", "killed", "running", "killing", "cancelled", "paused"]) {
            expect(getStatusStyle(state)?.dimIcon).toBeUndefined()
            expect(getStatusStyle(state)?.label).toBeUndefined()
        }
    })
})

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
