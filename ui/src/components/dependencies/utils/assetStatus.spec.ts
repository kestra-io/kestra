import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {durationUtils} from "@kestra-io/design-system"
import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"
import {compactAge, normalizeStatus, statusColorOf, statusIconOf} from "./assetStatus"

const NOW = new Date("2026-11-14T12:00:00Z")

describe("normalizeStatus", () => {
    it.each(["fresh", "stale", "failed", "unknown"])(
        "should keep the known status %s",
        (status) => {
            expect(normalizeStatus(status)).toBe(status)
        },
    )

    it("should fall back to unknown for an unrecognised or missing status", () => {
        expect(normalizeStatus("pending")).toBe("unknown")
        expect(normalizeStatus("FRESH")).toBe("unknown")
        expect(normalizeStatus("")).toBe("unknown")
        expect(normalizeStatus()).toBe("unknown")
    })
})

describe("statusIconOf", () => {
    it.each([
        ["fresh", CheckCircle],
        ["stale", ClockAlertOutline],
        ["failed", AlertCircle],
        ["unknown", HelpCircleOutline],
        ["other", HelpCircleOutline],
        [undefined, HelpCircleOutline],
    ])("should map %s to its expected icon", (status, expectedIcon) => {
        expect(statusIconOf(status)).toBe(expectedIcon)
    })

    it("should use four distinct icons for the four known statuses", () => {
        const icons = ["fresh", "stale", "failed", "unknown"].map((status) =>
            statusIconOf(status),
        )

        expect(new Set(icons).size).toBe(4)
    })
})

describe("statusColorOf", () => {
    it.each([
        ["fresh", "var(--ks-status-success)"],
        ["stale", "var(--ks-status-warning)"],
        ["failed", "var(--ks-status-error)"],
        ["unknown", "var(--ks-status-neutral)"],
    ])("should return the expected color for %s", (status, expectedColor) => {
        expect(statusColorOf(status)).toBe(expectedColor)
    })

    it("should use the neutral token for an unrecognised or missing status", () => {
        expect(statusColorOf("unexpected")).toBe("var(--ks-status-neutral)")
        expect(statusColorOf()).toBe("var(--ks-status-neutral)")
    })
})

describe("compactAge", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(NOW)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("should return undefined for a missing or invalid date", () => {
        expect(compactAge()).toBeUndefined()
        expect(compactAge("")).toBeUndefined()
        expect(compactAge("not-a-valid-date")).toBeUndefined()
    })

    it("should return undefined for a date in the future", () => {
        expect(compactAge("2026-11-14T12:05:00Z")).toBeUndefined()
    })

    it("should return the compact duration for the exact current-time boundary", () => {
        expect(compactAge(NOW.toISOString())).toBe(durationUtils.humanDuration(0))
    })

    it("should format past timestamps as compact durations", () => {
        expect(compactAge("2026-11-14T11:00:00Z")).toBe("1h")
        expect(compactAge("2026-11-12T09:00:00Z")).toBe("2d, 3h")
    })
})
