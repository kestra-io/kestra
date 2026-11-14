import {afterEach, describe, expect, it, vi} from "vitest"
import {durationUtils} from "@kestra-io/design-system"
import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"
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

    it.each([
        ["fresh", CheckCircle],
        ["stale", ClockAlertOutline],
        ["failed", AlertCircle],
        ["unknown", HelpCircleOutline],
        ["other", HelpCircleOutline],
        [undefined, HelpCircleOutline],
    ])("maps status %s to its expected icon component", (input, expectedIcon) => {
        expect(statusIconOf(input)).toBe(expectedIcon)
    })

    it("uses four distinct icons across the four known statuses", () => {
        const icons = ["fresh", "stale", "failed", "unknown"].map((status) => statusIconOf(status))
        expect(new Set(icons).size).toBe(4)
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

    it("formats a past timestamp and the exact current-time boundary as a compact duration", () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date("2026-11-14T12:00:00Z"))

        expect(compactAge("2026-11-14T12:00:00Z")).toBe(durationUtils.humanDuration(0))
        expect(compactAge("2026-11-14T11:00:00Z")).toBe("1h")
        expect(compactAge("2026-11-12T09:00:00Z")).toBe("2d, 3h")
    })
})
