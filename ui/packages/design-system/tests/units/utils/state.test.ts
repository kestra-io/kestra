import {describe, test, expect} from "vitest"
import {
    getNonRunningStates,
    isFailed,
    isKillable,
    isPaused,
    isQueued,
    isRunning,
    isTerminated,
} from "../../../src/utils/state"

describe("isPaused", () => {
    test("is true only for PAUSED", () => {
        expect(isPaused("PAUSED")).toBe(true)
        expect(isPaused("RUNNING")).toBe(false)
        expect(isPaused("SUCCESS")).toBe(false)
    })

    test("is false for an unknown state", () => {
        expect(isPaused("NOT_A_STATE")).toBe(false)
    })
})

describe("isQueued", () => {
    test("is true only for QUEUED", () => {
        expect(isQueued("QUEUED")).toBe(true)
        expect(isQueued("RUNNING")).toBe(false)
        expect(isQueued("RETRYING")).toBe(false)
    })

    test("is false for an unknown state", () => {
        expect(isQueued("NOT_A_STATE")).toBe(false)
    })
})

describe("isRunning", () => {
    test("is true for every running state", () => {
        expect(isRunning("SUBMITTED")).toBe(true)
        expect(isRunning("CREATED")).toBe(true)
        expect(isRunning("PAUSED")).toBe(true)
        expect(isRunning("BREAKPOINT")).toBe(true)
        expect(isRunning("RUNNING")).toBe(true)
        expect(isRunning("KILLING")).toBe(true)
    })

    test("is false for every non-running state", () => {
        expect(isRunning("RESTARTED")).toBe(false)
        expect(isRunning("SUCCESS")).toBe(false)
        expect(isRunning("KILLED")).toBe(false)
        expect(isRunning("WARNING")).toBe(false)
        expect(isRunning("FAILED")).toBe(false)
        expect(isRunning("CANCELLED")).toBe(false)
        expect(isRunning("SKIPPED")).toBe(false)
        expect(isRunning("QUEUED")).toBe(false)
        expect(isRunning("RETRYING")).toBe(false)
        expect(isRunning("RETRIED")).toBe(false)
    })

    test("is undefined for an unknown state", () => {
        expect(isRunning("NOT_A_STATE")).toBe(undefined)
    })
})

describe("isKillable", () => {
    test("is true for every killable state", () => {
        expect(isKillable("SUBMITTED")).toBe(true)
        expect(isKillable("CREATED")).toBe(true)
        expect(isKillable("RESTARTED")).toBe(true)
        expect(isKillable("RUNNING")).toBe(true)
        expect(isKillable("KILLING")).toBe(true)
        expect(isKillable("PAUSED")).toBe(true)
        expect(isKillable("RETRYING")).toBe(true)
        expect(isKillable("BREAKPOINT")).toBe(true)
    })

    test("is false for every non-killable state", () => {
        expect(isKillable("SUCCESS")).toBe(false)
        expect(isKillable("KILLED")).toBe(false)
        expect(isKillable("WARNING")).toBe(false)
        expect(isKillable("FAILED")).toBe(false)
        expect(isKillable("CANCELLED")).toBe(false)
        expect(isKillable("SKIPPED")).toBe(false)
        expect(isKillable("QUEUED")).toBe(false)
        expect(isKillable("RETRIED")).toBe(false)
    })

    test("is undefined for an unknown state", () => {
        expect(isKillable("NOT_A_STATE")).toBe(undefined)
    })
})

describe("isFailed", () => {
    test("is true for every failed state", () => {
        expect(isFailed("KILLING")).toBe(true)
        expect(isFailed("KILLED")).toBe(true)
        expect(isFailed("WARNING")).toBe(true)
        expect(isFailed("FAILED")).toBe(true)
        expect(isFailed("CANCELLED")).toBe(true)
        expect(isFailed("SKIPPED")).toBe(true)
    })

    test("is false for every non-failed state", () => {
        expect(isFailed("SUBMITTED")).toBe(false)
        expect(isFailed("CREATED")).toBe(false)
        expect(isFailed("RESTARTED")).toBe(false)
        expect(isFailed("SUCCESS")).toBe(false)
        expect(isFailed("RUNNING")).toBe(false)
        expect(isFailed("PAUSED")).toBe(false)
        expect(isFailed("QUEUED")).toBe(false)
        expect(isFailed("RETRYING")).toBe(false)
        expect(isFailed("RETRIED")).toBe(false)
        expect(isFailed("BREAKPOINT")).toBe(false)
    })

    test("is undefined for an unknown state", () => {
        expect(isFailed("NOT_A_STATE")).toBe(undefined)
    })
})

describe("isTerminated", () => {
    test("is true for every terminated state", () => {
        expect(isTerminated("SUCCESS")).toBe(true)
        expect(isTerminated("WARNING")).toBe(true)
        expect(isTerminated("FAILED")).toBe(true)
        expect(isTerminated("KILLED")).toBe(true)
        expect(isTerminated("CANCELLED")).toBe(true)
        expect(isTerminated("RETRIED")).toBe(true)
        expect(isTerminated("SKIPPED")).toBe(true)
        expect(isTerminated("RESUBMITTED")).toBe(true)
    })

    test("is false for every non-terminated state", () => {
        expect(isTerminated("SUBMITTED")).toBe(false)
        expect(isTerminated("CREATED")).toBe(false)
        expect(isTerminated("RESTARTED")).toBe(false)
        expect(isTerminated("RUNNING")).toBe(false)
        expect(isTerminated("KILLING")).toBe(false)
        expect(isTerminated("PAUSED")).toBe(false)
        expect(isTerminated("QUEUED")).toBe(false)
        expect(isTerminated("RETRYING")).toBe(false)
        expect(isTerminated("BREAKPOINT")).toBe(false)
    })

    test("is false for an unknown state", () => {
        expect(isTerminated("NOT_A_STATE")).toBe(false)
    })
})

describe("getNonRunningStates", () => {
    test("returns the name of every non-running state, in table order", () => {
        expect(getNonRunningStates()).toEqual([
            "RESTARTED",
            "SUCCESS",
            "KILLED",
            "WARNING",
            "FAILED",
            "CANCELLED",
            "SKIPPED",
            "QUEUED",
            "RETRYING",
            "RETRIED",
        ])
    })
})