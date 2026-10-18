import {describe, expect, it} from "vitest"
import type {Notification} from "@kestra-io/kestra-sdk"
import {progressColor, progressPercentage} from "./notificationProgress"

describe("notificationProgress", () => {
    describe("progressColor", () => {
        it("returns a distinct colour per outcome: SUCCEEDED, PARTIAL, and FAILED", () => {
            const succeeded = progressColor({outcome: "SUCCEEDED"} as Notification)
            const partial = progressColor({outcome: "PARTIAL"} as Notification)
            const failed = progressColor({outcome: "FAILED"} as Notification)

            expect(succeeded).toBe("var(--ks-status-success)")
            expect(partial).toBe("var(--ks-status-warning)")
            expect(failed).toBe("var(--ks-status-failed)")

            // Verify they are distinct
            const uniqueColors = new Set([succeeded, partial, failed])
            expect(uniqueColors.size).toBe(3)
        })

        it("falls back to the RUNNING colour when a notification has no outcome", () => {
            const withoutOutcome = progressColor({} as Notification)
            expect(withoutOutcome).toBe("var(--ks-status-running)")

            const withUndefinedOutcome = progressColor({outcome: undefined} as Notification)
            expect(withUndefinedOutcome).toBe("var(--ks-status-running)")
        })
    })

    describe("progressPercentage", () => {
        it("returns null when totalItems is missing or zero", () => {
            expect(progressPercentage({} as Notification)).toBeNull()
            expect(progressPercentage({totalItems: 0} as Notification)).toBeNull()
            expect(progressPercentage({totalItems: undefined} as Notification)).toBeNull()
        })

        it("counts both succeeded and failed items toward progress", () => {
            const notification = {
                totalItems: 10,
                succeededItems: 4,
                failedItems: 2,
            } as Notification

            // (4 + 2) / 10 = 60%
            expect(progressPercentage(notification)).toBe(60)
        })

        it("treats missing succeededItems or failedItems as zero rather than producing NaN", () => {
            const missingFailed = {
                totalItems: 10,
                succeededItems: 7,
            } as Notification
            expect(progressPercentage(missingFailed)).toBe(70)

            const missingSucceeded = {
                totalItems: 10,
                failedItems: 3,
            } as Notification
            expect(progressPercentage(missingSucceeded)).toBe(30)

            const missingBoth = {
                totalItems: 10,
            } as Notification
            expect(progressPercentage(missingBoth)).toBe(0)
        })

        it("rounds and clamps the result so it never exceeds 100", () => {
            // Rounding check: 1 / 3 = 33.333% -> 33
            const roundDown = {
                totalItems: 3,
                succeededItems: 1,
                failedItems: 0,
            } as Notification
            expect(progressPercentage(roundDown)).toBe(33)

            // Rounding check: 2 / 3 = 66.666% -> 67
            const roundUp = {
                totalItems: 3,
                succeededItems: 2,
                failedItems: 0,
            } as Notification
            expect(progressPercentage(roundUp)).toBe(67)

            // Clamping check: 15 / 10 = 150% -> clamped to 100
            const overflow = {
                totalItems: 10,
                succeededItems: 12,
                failedItems: 3,
            } as Notification
            expect(progressPercentage(overflow)).toBe(100)
        })
    })
})
