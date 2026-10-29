import {describe, expect, it} from "vitest"
import type {Notification} from "@kestra-io/kestra-sdk"
import {progressColor, progressPercentage} from "./notificationProgress"

function notification(overrides: Partial<Notification> = {}): Notification {
    return {
        type: "BULK_OPERATION",
        title: "Bulk operation",
        createdDate: "2024-01-01T00:00:00Z",
        updatedDate: "2024-01-01T00:00:00Z",
        ...overrides,
    }
}

describe("progressColor", () => {
    it("returns a distinct colour per outcome: SUCCEEDED, PARTIAL and FAILED", () => {
        const succeeded = progressColor(notification({outcome: "SUCCEEDED"}))
        const partial = progressColor(notification({outcome: "PARTIAL"}))
        const failed = progressColor(notification({outcome: "FAILED"}))

        expect(succeeded).toBe("var(--ks-status-success)")
        expect(partial).toBe("var(--ks-status-warning)")
        expect(failed).toBe("var(--ks-status-failed)")
        expect(new Set([succeeded, partial, failed]).size).toBe(3)
    })

    it("falls back to the RUNNING colour when the notification has no outcome", () => {
        expect(progressColor(notification())).toBe("var(--ks-status-running)")
        expect(progressColor(notification({outcome: null}))).toBe("var(--ks-status-running)")
    })
})

describe("progressPercentage", () => {
    it("returns null when totalItems is missing or zero", () => {
        expect(progressPercentage(notification())).toBeNull()
        expect(progressPercentage(notification({totalItems: null}))).toBeNull()
        expect(progressPercentage(notification({totalItems: 0}))).toBeNull()
    })

    it("counts both succeeded and failed items toward progress", () => {
        expect(progressPercentage(notification({totalItems: 10, succeededItems: 3, failedItems: 2}))).toBe(50)
    })

    it("counts missing succeededItems or failedItems as zero rather than producing NaN", () => {
        expect(progressPercentage(notification({totalItems: 10, succeededItems: 5}))).toBe(50)
        expect(progressPercentage(notification({totalItems: 10, failedItems: 4}))).toBe(40)
        expect(progressPercentage(notification({totalItems: 10}))).toBe(0)
        expect(progressPercentage(notification({totalItems: 10, succeededItems: null, failedItems: null}))).toBe(0)
    })

    it("rounds the result and clamps it so it never exceeds 100", () => {
        expect(progressPercentage(notification({totalItems: 3, succeededItems: 1, failedItems: 0}))).toBe(33)
        expect(progressPercentage(notification({totalItems: 3, succeededItems: 2, failedItems: 0}))).toBe(67)
        expect(progressPercentage(notification({totalItems: 10, succeededItems: 8, failedItems: 5}))).toBe(100)
    })
})
