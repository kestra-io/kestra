import {describe, expect, it} from "vitest"
import {computeDurationBreakdown} from "../../../src/misc/durationBreakdown"

describe("computeDurationBreakdown", () => {
    it("should return zeroed breakdown when there is no history", () => {
        const result = computeDurationBreakdown([])

        expect(result).toEqual({total: 0, queued: 0, running: 0, paused: 0, isRunning: false})
    })

    it("should return zero duration when the task never ran (single terminal entry)", () => {
        const result = computeDurationBreakdown([
            {date: 1_000, state: "SKIPPED"},
        ])

        expect(result).toEqual({total: 0, queued: 0, running: 0, paused: 0, isRunning: false})
    })

    it("should count elapsed time as queued while waiting for a single pending entry to start running", () => {
        const result = computeDurationBreakdown(
            [{date: 1_000, state: "CREATED"}],
            1_420,
        )

        expect(result).toEqual({total: 420, queued: 420, running: 0, paused: 0, isRunning: true})
    })

    it("should keep accumulating running time against now while the task is still running", () => {
        const result = computeDurationBreakdown(
            [
                {date: 1_000, state: "CREATED"},
                {date: 1_250, state: "RUNNING"},
            ],
            3_500,
        )

        expect(result).toEqual({total: 2_500, queued: 250, running: 2_250, paused: 0, isRunning: true})
    })

    it("should bill an inter-attempt gap as queued rather than running", () => {
        const result = computeDurationBreakdown([
            {date: 0, state: "CREATED"},
            {date: 100, state: "RUNNING"},
            {date: 1_100, state: "FAILED"},
            {date: 3_100, state: "RETRYING"},
            {date: 3_200, state: "RUNNING"},
            {date: 4_200, state: "SUCCESS"},
        ])

        expect(result.queued).toBe(2_200)
        expect(result.running).toBe(2_000)
        expect(result.total).toBe(4_200)
        expect(result.queued + result.running).toBe(result.total)
        expect(result.isRunning).toBe(false)
    })

    it("should compute a correct total across a midnight boundary", () => {
        const beforeMidnight = Date.UTC(2026, 0, 1, 23, 59, 0)
        const afterMidnight = Date.UTC(2026, 0, 2, 0, 5, 0)

        const result = computeDurationBreakdown([
            {date: beforeMidnight, state: "CREATED"},
            {date: beforeMidnight + 60_000, state: "RUNNING"},
            {date: afterMidnight, state: "SUCCESS"},
        ])

        expect(result.total).toBe(afterMidnight - beforeMidnight)
        expect(result.queued + result.running).toBe(result.total)
        expect(result.running).toBe(afterMidnight - (beforeMidnight + 60_000))
    })

    it("should bill a pause as paused rather than queued or running", () => {
        const result = computeDurationBreakdown([
            {date: 0, state: "CREATED"},
            {date: 100, state: "RUNNING"},
            {date: 1_100, state: "PAUSED"},
            {date: 14_401_100, state: "RUNNING"},
            {date: 14_403_100, state: "SUCCESS"},
        ])

        expect(result.paused).toBe(14_400_000)
        expect(result.running).toBe(3_000)
        expect(result.queued).toBe(100)
        expect(result.queued + result.running + result.paused).toBe(result.total)
    })

    it("should keep accumulating paused time against now while the task is still paused", () => {
        const result = computeDurationBreakdown(
            [
                {date: 0, state: "CREATED"},
                {date: 100, state: "RUNNING"},
                {date: 1_100, state: "PAUSED"},
            ],
            5_100,
        )

        expect(result.paused).toBe(4_000)
        expect(result.running).toBe(1_000)
        expect(result.queued).toBe(100)
        expect(result.isRunning).toBe(true)
    })

    it("should treat a breakpoint as paused and a killing teardown as running", () => {
        const breakpoint = computeDurationBreakdown([
            {date: 0, state: "RUNNING"},
            {date: 500, state: "BREAKPOINT"},
            {date: 2_500, state: "RUNNING"},
            {date: 3_000, state: "SUCCESS"},
        ])

        expect(breakpoint.paused).toBe(2_000)
        expect(breakpoint.running).toBe(1_000)

        const killed = computeDurationBreakdown([
            {date: 0, state: "RUNNING"},
            {date: 1_000, state: "KILLING"},
            {date: 1_250, state: "KILLED"},
        ])

        expect(killed.running).toBe(1_250)
        expect(killed.paused).toBe(0)
        expect(killed.queued).toBe(0)
    })

    it("should accept ISO string and Moment-like dates, sorting out-of-order entries", () => {
        const result = computeDurationBreakdown([
            {date: "2026-01-01T00:00:01.000Z", state: "RUNNING"},
            {date: "2026-01-01T00:00:00.000Z", state: "CREATED"},
            {date: "2026-01-01T00:00:03.000Z", state: "SUCCESS"},
        ])

        expect(result.queued).toBe(1_000)
        expect(result.running).toBe(2_000)
        expect(result.total).toBe(3_000)
    })
})
