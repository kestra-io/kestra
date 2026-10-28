import {describe, expect, it} from "vitest"
import {NOT_STARTED, stateBarSegments} from "./stateBar"

const statesOf = (segments: ReturnType<typeof stateBarSegments>) => segments.map((segment) => segment.state)

describe("stateBarSegments", () => {
    it("shouldOrderSuccessThenFailuresThenOtherTerminalThenRunningThenNotStarted", () => {
        const segments = stateBarSegments(
            {RUNNING: 1, SKIPPED: 1, KILLED: 1, FAILED: 1, WARNING: 1, SUCCESS: 1, CANCELLED: 1},
            {total: 10},
        )

        expect(statesOf(segments)).toEqual(["SUCCESS", "WARNING", "FAILED", "KILLED", "SKIPPED", "CANCELLED", "RUNNING", NOT_STARTED])
    })

    it("shouldDropZeroNegativeAndNonNumericCounts", () => {
        const segments = stateBarSegments({SUCCESS: 3, FAILED: 0, KILLED: -2, WARNING: Number.NaN, SKIPPED: "4" as unknown as number})

        expect(statesOf(segments)).toEqual(["SUCCESS"])
    })

    it("shouldAddTheRunningCountToTheRunningStateOfTheMap", () => {
        const segments = stateBarSegments({SUCCESS: 2, RUNNING: 1}, {running: 3})

        expect(segments.find((segment) => segment.state === "RUNNING")?.count).toBe(4)
    })

    it("shouldFillTheRemainderOfTheTotalWithNotStarted", () => {
        const segments = stateBarSegments({SUCCESS: 4812, FAILED: 3}, {total: 5000, running: 12})
        const notStarted = segments[segments.length - 1]

        expect(notStarted).toMatchObject({state: NOT_STARTED, count: 173, notStarted: true})
    })

    it("shouldNotAddNotStartedWhenCountsReachOrExceedTheTotal", () => {
        expect(statesOf(stateBarSegments({SUCCESS: 5}, {total: 5}))).toEqual(["SUCCESS"])
        expect(statesOf(stateBarSegments({SUCCESS: 6}, {total: 5}))).toEqual(["SUCCESS"])
    })

    it("shouldRenderNothingWhenThereIsNothingToCount", () => {
        expect(stateBarSegments(undefined)).toEqual([])
        expect(stateBarSegments({}, {total: 0})).toEqual([])
    })

    it("shouldColourKnownStatesFromTheirStatusTokenAndFallBackToNeutral", () => {
        const segments = stateBarSegments({FAILED: 1, MYSTERY: 2})

        expect(segments.map((segment) => segment.color)).toEqual(["var(--ks-status-failed)", "var(--ks-status-neutral)"])
    })

    it("shouldPlaceUnknownStatesAfterEveryKnownOne", () => {
        expect(statesOf(stateBarSegments({MYSTERY: 1, CREATED: 1, SUCCESS: 1}))).toEqual(["SUCCESS", "CREATED", "MYSTERY"])
    })
})
