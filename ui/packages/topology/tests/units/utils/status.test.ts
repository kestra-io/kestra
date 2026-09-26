import {test, expect, describe} from "vitest"
import {getStatusStyle} from "../../../src/utils/status"

describe("getStatusStyle", () => {
    test.each([
        ["success", "--ks-text-success", "var(--ks-topology-bg-success)", "var(--ks-border-success)"],
        ["failed", "--ks-text-error", "var(--ks-topology-bg-errors)", "var(--ks-topology-border-errors)"],
        ["killed", "--ks-text-error", "var(--ks-topology-bg-errors)", "var(--ks-topology-border-errors)"],
        ["running", "--ks-status-running", "var(--ks-topology-bg-running)", "var(--ks-topology-border-running)"],
        ["killing", "--ks-status-pending", "var(--ks-topology-bg-killing)", "var(--ks-topology-border-killing)"],
        ["skipped", "--ks-status-neutral", undefined, undefined],
        ["cancelled", "--ks-status-neutral", "var(--ks-topology-bg-cancelled)", "var(--ks-topology-border-cancelled)"],
    ])("returns the correct style for %s", (state, textVar, bg, border) => {
        const style = getStatusStyle(state)

        expect(style?.textVar).toBe(textVar)
        expect(style?.bg).toBe(bg)
        expect(style?.border).toBe(border)
    })

    test("handles uppercase states", () => {
        expect(getStatusStyle("SUCCESS")?.textVar).toBe("--ks-text-success")
    })

    test("returns the neutral style for an unknown state", () => {
        const style = getStatusStyle("paused")

        expect(style?.textVar).toBe("--ks-status-neutral")
        expect(style?.bg).toBeUndefined()
        expect(style?.border).toBeUndefined()
    })

    test.each([undefined, null, ""])("returns undefined for empty state %s", (state) => {
        expect(getStatusStyle(state)).toBeUndefined()
    })

    test("adds dimIcon and label only to skipped", () => {
        expect(getStatusStyle("skipped")?.dimIcon).toBe(true)
        expect(getStatusStyle("skipped")?.label).toBe("skipped")

        for (const state of ["success", "failed", "killed", "running", "killing", "cancelled", "paused"]) {
            expect(getStatusStyle(state)?.dimIcon).toBeUndefined()
            expect(getStatusStyle(state)?.label).toBeUndefined()
        }
    })
})