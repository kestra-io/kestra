import {describe, it, expect, vi, afterEach} from "vitest"

import {statusIconOf, compactAge} from "../../../../src/components/dependencies/utils/assetStatus"

describe("dependencies assetStatus", () => {
    afterEach(() => {
        vi.useRealTimers()
    })

    // P1. Both states render in --ks-status-neutral, so the glyph shape is the only thing
    // separating "has never run" from "we do not track this". Editing the map could silently
    // collapse them into one signal.
    it("gives never and unknown different glyphs, since both render neutral", () => {
        expect(statusIconOf("never")).not.toBe(statusIconOf("unknown"))
        expect(statusIconOf("unrecognised")).toBe(statusIconOf("unknown"))
        // Inherited keys resolve off a plain object literal, so an unnormalised lookup
        // handed `<component :is>` a Function instead of an icon.
        expect(statusIconOf("toString")).toBe(statusIconOf("unknown"))
    })

    // P1. Depends on humanDuration pinning largest: 2 internally and on the design system's
    // single-letter language packs, neither of which is obvious from the call site, and both
    // of which are what let this ship without a new i18n key.
    it("formats an age in compact units", () => {
        vi.useFakeTimers()
        vi.setSystemTime(Date.parse("2026-08-21T12:00:00Z"))

        expect(compactAge("2026-08-21T11:00:00Z")).toBe("1h")
        expect(compactAge("2026-08-21T11:55:00Z")).toBe("5m")
    })

    // P1. Clock skew between the worker that wrote the asset and the browser is real, and
    // without the guard it renders a negative duration in the row.
    it("returns undefined for a timestamp in the future", () => {
        vi.useFakeTimers()
        vi.setSystemTime(Date.parse("2026-08-21T12:00:00Z"))

        expect(compactAge("2026-08-21T13:00:00Z")).toBeUndefined()
    })
})
