import {describe, expect, it} from "vitest"
import {
    loopChip,
    loopChipFromIterations,
    loopIterationCountsOf,
    loopOutcome,
    loopTaskContext,
    taskRunStateCountsOf,
    type LoopLaneData,
} from "./loopOutcome"

const outcomeOf = (data: Parameters<typeof loopOutcome>[0]) => {
    const outcome = loopOutcome(data)
    if (!outcome) throw new Error("no outcome")
    return outcome
}

describe("loopOutcome", () => {
    it("shouldReturnNothingWhenNoIterationCount", () => {
        expect(loopOutcome({})).toBeUndefined()
    })

    it("shouldCountNotStartedIterationsFromRunningAndTerminated", () => {
        const outcome = outcomeOf({iterationCount: 4, runningIterations: 0, terminatedIterations: {FAILED: 1, SUCCESS: 1}, state: "FAILED"})

        expect(outcome).toMatchObject({total: 4, done: 2, running: 0, failed: 1, notStarted: 2, finished: true})
    })

    it("shouldNotRepeatTheStateTheChipAlreadySays", () => {
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "FAILED"}).state).toBeUndefined()
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "SUCCESS"}).state).toBeUndefined()
        expect(outcomeOf({iterationCount: 4, runningIterations: 1, terminatedIterations: {SUCCESS: 3}, state: "RUNNING"}).state).toBeUndefined()
    })

    it("shouldShowTheStateTheChipCannotSay", () => {
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {SUCCESS: 2}, state: "KILLED"}).state).toBe("KILLED")
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "WARNING"}).state).toBe("WARNING")
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "SUCCESS"}).state).toBe("SUCCESS")
        expect(outcomeOf({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "FAILED"}).state).toBe("FAILED")
    })
})

describe("loopChip", () => {
    it("shouldSayHowManyFailedOfTotal", () => {
        const chip = loopChip(outcomeOf({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 3}, state: "FAILED"}))

        expect(chip).toMatchObject({kind: "failed", failed: 1, total: 4})
    })

    it("shouldMentionIterationsThatNeverStarted", () => {
        const chip = loopChip(outcomeOf({iterationCount: 4, terminatedIterations: {FAILED: 1, SUCCESS: 1}, state: "FAILED"}))

        expect(chip).toMatchObject({kind: "failed-not-started", failed: 1, notStarted: 2, total: 4})
    })

    it("shouldSayIterationsWhenNoneFailed", () => {
        const chip = loopChip(outcomeOf({iterationCount: 4, terminatedIterations: {SUCCESS: 4}, state: "SUCCESS"}))

        expect(chip).toMatchObject({kind: "success", total: 4})
    })

    it("shouldShowProgressWhileRunning", () => {
        const chip = loopChip(outcomeOf({iterationCount: 4, runningIterations: 1, terminatedIterations: {FAILED: 1, SUCCESS: 1}, state: "RUNNING"}))

        expect(chip).toMatchObject({kind: "progress", done: 2, failed: 1, total: 4})
    })

    it("shouldNotClaimSuccessWhenAKilledLoopLeftIterationsUnstarted", () => {
        const chip = loopChip(outcomeOf({iterationCount: 4, terminatedIterations: {SUCCESS: 2}, state: "KILLED"}))

        expect(chip).toMatchObject({kind: "progress", done: 2, notStarted: 2})
    })

    it("shouldBuildAnIterationChipFromNestedCounts", () => {
        expect(loopChipFromIterations({runs: 480, failed: 3})).toMatchObject({kind: "failed", failed: 3, total: 480})
        expect(loopChipFromIterations({runs: 12, failed: 0})).toMatchObject({kind: "success", total: 12})
    })
})

describe("outputs counts", () => {
    it("shouldReadBothCountMapsAndIgnoreAbsentOnes", () => {
        const outputs = {taskRunStateCounts: {upload: {FAILED: 3}}, loopIterationCounts: {per_invoice: {SUCCESS: 477}}}

        expect(taskRunStateCountsOf(outputs)).toEqual({upload: {FAILED: 3}})
        expect(loopIterationCountsOf(outputs)).toEqual({per_invoice: {SUCCESS: 477}})
        expect(taskRunStateCountsOf({})).toBeUndefined()
        expect(loopIterationCountsOf(undefined)).toBeUndefined()
    })
})

describe("loopTaskContext", () => {
    const lane = (overrides: Partial<LoopLaneData>): LoopLaneData => ({taskId: "x", status: "ready", parentScoped: true, ...overrides})

    it("shouldIgnoreTasksOutsideAnyLoop", () => {
        expect(loopTaskContext("other.task", "task", {per_region: lane({})})).toBeUndefined()
    })

    it("shouldSummarizeRunsWhileTheLoopIsUnscoped", () => {
        const lanes = {per_region: lane({taskRunStateCounts: {upload: {FAILED: 3, SKIPPED: 477}}})}

        expect(loopTaskContext("per_region.upload", "upload", lanes)).toEqual({unscoped: true, summary: {runs: 480, failed: 3}})
    })

    it("shouldShowNoNumberWhenTheCountsKeyIsAbsent", () => {
        expect(loopTaskContext("per_region.upload", "upload", {per_region: lane({})})).toEqual({unscoped: true, summary: undefined})
    })

    it("shouldFallBackToTheOuterLoopCountsWhenTheInnerLoopHasNoRun", () => {
        const lanes = {
            per_region: lane({taskRunStateCounts: {render: {SUCCESS: 480}}}),
            "per_region.per_invoice": lane({status: "nested"}),
        }

        expect(loopTaskContext("per_region.per_invoice.render", "render", lanes)?.summary).toEqual({runs: 480, failed: 0})
    })

    it("shouldReportScopedWhenTheInnermostLoopHasAnIteration", () => {
        const lanes = {per_region: lane({scopedNumber: 2, taskRunStateCounts: {upload: {FAILED: 1}}})}

        expect(loopTaskContext("per_region.upload", "upload", lanes)).toEqual({unscoped: false})
    })
})
