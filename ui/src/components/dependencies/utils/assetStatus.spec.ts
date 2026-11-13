import {afterEach, describe, expect, it, vi} from "vitest"
import {compactAge, normalizeStatus, statusColorOf, statusIconOf} from "./assetStatus"

describe("assetStatus", () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    it("returns each known status unchanged", () => {
        expect(normalizeStatus("fresh")).toBe("fresh")
        expect(normalizeStatus("stale")).toBe("stale")
        expect(normalizeStatus("failed")).toBe("failed")
        expect(normalizeStatus("unknown")).toBe("unknown")
    })

    it("normalizes unknown or omitted statuses to unknown", () => {
        expect(normalizeStatus("pending")).toBe("unknown")
        expect(normalizeStatus("")).toBe("unknown")
        expect(normalizeStatus()).toBe("unknown")
    })

    it("returns a distinct icon per known status and the unknown icon otherwise", () => {
        const fresh = statusIconOf("fresh")
        const stale = statusIconOf("stale")
        const failed = statusIconOf("failed")
        const unknown = statusIconOf("unknown")

        expect(new Set([fresh, stale, failed, unknown]).size).toBe(4)
        expect(statusIconOf("other")).toBe(unknown)
        expect(statusIconOf()).toBe(unknown)
    })

    it("wraps the matching status token in var() with neutral fallback", () => {
        expect(statusColorOf("fresh")).toBe("var(--ks-status-success)")
        expect(statusColorOf("stale")).toBe("var(--ks-status-warning)")
        expect(statusColorOf("failed")).toBe("var(--ks-status-error)")
        expect(statusColorOf("unknown")).toBe("var(--ks-status-neutral)")
        expect(statusColorOf("unexpected")).toBe("var(--ks-status-neutral)")
        expect(statusColorOf()).toBe("var(--ks-status-neutral)")
    })

    it("returns undefined for missing, invalid, or future dates", () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date("2026-11-14T12:00:00Z"))

        expect(compactAge()).toBeUndefined()
        expect(compactAge("")).toBeUndefined()
        expect(compactAge("not-a-valid-date")).toBeUndefined()
        expect(compactAge("2026-11-14T12:05:00Z")).toBeUndefined()
    })

    it("formats a past timestamp as a compact duration", () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date("2026-11-14T12:00:00Z"))

        expect(compactAge("2026-11-14T11:00:00Z")).toBe("1h")
        expect(compactAge("2026-11-12T09:00:00Z")).toBe("2d, 3h")
    })
})
