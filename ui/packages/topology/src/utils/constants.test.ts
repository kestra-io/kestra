import {describe, expect, it} from "vitest"
import {PILL_COMPENSATION, ZOOM_LOD, pillZoomCompensation} from "./constants"

describe("pillZoomCompensation (kestra-io/kestra#21619)", () => {
    it("should scale nothing once the canvas is at or above 1:1", () => {
        expect(pillZoomCompensation(1)).toBe(1)
        expect(pillZoomCompensation(2)).toBe(1)
    })

    it("should hold the ring near one screen pixel from the pill threshold down to the cap", () => {
        // 0.49 sits just under the pill threshold, 0.29 is where a 13-task execution lands at
        // fit-to-view, and 0.25 is the last zoom the cap can still fully compensate. The band is
        // wider than a single value because the ring is quantised to whole pixels.
        for (const zoom of [0.49, 0.45, 0.4, 0.35, 0.3, 0.29, 0.25]) {
            const onScreen = pillZoomCompensation(zoom) * zoom
            expect(onScreen).toBeGreaterThan(0.8)
            expect(onScreen).toBeLessThan(1.25)
        }
    })

    it("should stay bounded as the zoom approaches MIN_ZOOM, rather than growing without limit", () => {
        expect(pillZoomCompensation(0.02)).toBe(PILL_COMPENSATION.RING_MAX)
        expect(pillZoomCompensation(0.0001)).toBe(PILL_COMPENSATION.RING_MAX)
    })

    it("should fall back to no scaling for a zoom that is not a usable number", () => {
        expect(pillZoomCompensation(0)).toBe(1)
        expect(pillZoomCompensation(-1)).toBe(1)
        expect(pillZoomCompensation(Number.NaN)).toBe(1)
        expect(pillZoomCompensation(Number.POSITIVE_INFINITY)).toBe(1)
    })

    it("should quantise to whole pixels, so panning the zoom repaints the nodes a handful of times", () => {
        const seen = new Set<number>()
        for (let zoom = ZOOM_LOD.PILL; zoom > 0.02; zoom -= 0.002) seen.add(pillZoomCompensation(zoom))

        // Whole pixels across the whole pill range means 2, 3 and 4 and nothing in between. A
        // sub-pixel step would multiply this, repainting every node for a width no browser draws.
        expect([...seen].sort()).toEqual([2, 3, PILL_COMPENSATION.RING_MAX])
    })
})
