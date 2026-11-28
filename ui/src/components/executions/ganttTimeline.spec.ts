import {describe, it, expect, beforeEach} from "vitest"

import {clampedBarStyle, formatGanttDuration} from "./ganttTimeline"

describe("clampedBarStyle", () => {
    it("keeps a width above the minimum unchanged", () => {
        expect(clampedBarStyle(10, 20, "var(--ks-status-success)")).toEqual({
            left: "10%",
            width: "20%",
            background: "var(--ks-status-success)",
        })
    })

    it("raises a width below the minimum to the minimum", () => {
        expect(clampedBarStyle(10, 1, "var(--ks-status-success)")).toEqual({
            left: "10%",
            width: "3%",
            background: "var(--ks-status-success)",
        })
    })

    it("pulls a start past the right-hand edge back so left plus width is at most 100", () => {
        const floored = clampedBarStyle(99, 1, "var(--ks-status-success)")
        expect(floored).toEqual({left: "97%", width: "3%", background: "var(--ks-status-success)"})
        expect(parseFloat(floored.left) + parseFloat(floored.width)).toBeLessThanOrEqual(100)

        const atEdge = clampedBarStyle(100, 20, "var(--ks-status-success)")
        expect(atEdge).toEqual({left: "80%", width: "20%", background: "var(--ks-status-success)"})
        expect(parseFloat(atEdge.left) + parseFloat(atEdge.width)).toBeLessThanOrEqual(100)
    })

    it("clamps a negative start to 0", () => {
        expect(clampedBarStyle(-25, 20, "var(--ks-status-success)")).toEqual({
            left: "0%",
            width: "20%",
            background: "var(--ks-status-success)",
        })
    })

    it("passes the background through untouched", () => {
        const style = clampedBarStyle(10, 20, "linear-gradient(var(--ks-status-success), var(--ks-status-error))")

        expect(style.background).toBe("linear-gradient(var(--ks-status-success), var(--ks-status-error))")
    })

    it("emits left and width as percentage strings", () => {
        const style = clampedBarStyle(10, 20, "var(--ks-status-success)")

        expect(style.left).toBe("10%")
        expect(style.width).toBe("20%")
        expect(typeof style.left).toBe("string")
        expect(typeof style.width).toBe("string")
    })
})

describe("formatGanttDuration", () => {
    beforeEach(() => localStorage.clear())

    it("converts milliseconds to a human duration", () => {
        expect(formatGanttDuration(45000)).toBe("45s")
        expect(formatGanttDuration(90000)).toBe("1m 30s")
        expect(formatGanttDuration(3660000)).toBe("1h 1m")
    })

    it("has its own stable output for 0", () => {
        expect(formatGanttDuration(0)).toBe("0s")
    })
})
