import {describe, expect, it} from "vitest"
import {placePeek, type PeekRect} from "./peekPlacement"

const CANVAS: PeekRect = {x: 0, y: 0, width: 1000, height: 800}
const PANEL = {width: 200, height: 100}
const GAP = 16

function anchorAt(x: number, y: number): PeekRect {
    return {x, y, width: 220, height: 60}
}

describe("placePeek", () => {
    it("should sit to the right of the task in a vertical flow", () => {
        const anchor = anchorAt(400, 300)

        const placement = placePeek({anchor, panel: PANEL, canvas: CANVAS, isHorizontal: false, gap: GAP})

        expect(placement.side).toBe("right")
        expect(placement.x).toBe(anchor.x + anchor.width + GAP)
        expect(placement.y).toBe(anchor.y + anchor.height / 2 - PANEL.height / 2)
    })

    it("should fall back to the left when a task sits where the right side would land", () => {
        const anchor = anchorAt(400, 300)
        const neighbour: PeekRect = {x: anchor.x + anchor.width + GAP, y: 280, width: 220, height: 60}

        const placement = placePeek({anchor, panel: PANEL, canvas: CANVAS, isHorizontal: false, otherRects: [neighbour], gap: GAP})

        expect(placement.side).toBe("left")
        expect(placement.x).toBe(anchor.x - GAP - PANEL.width)
    })

    it("should sit below the task in a horizontal flow", () => {
        const anchor = anchorAt(400, 300)

        const placement = placePeek({anchor, panel: PANEL, canvas: CANVAS, isHorizontal: true, gap: GAP})

        expect(placement.side).toBe("below")
        expect(placement.y).toBe(anchor.y + anchor.height + GAP)
        expect(placement.x).toBe(anchor.x + anchor.width / 2 - PANEL.width / 2)
    })

    it("should keep the panel inside the canvas when the task sits against an edge", () => {
        const anchor = anchorAt(CANVAS.width - 230, CANVAS.height - 20)

        const placement = placePeek({anchor, panel: PANEL, canvas: CANVAS, isHorizontal: false, gap: GAP})

        expect(placement.x).toBeLessThanOrEqual(CANVAS.width - PANEL.width)
        expect(placement.x).toBeGreaterThanOrEqual(0)
        expect(placement.y).toBeLessThanOrEqual(CANVAS.height - PANEL.height)
        expect(placement.y).toBeGreaterThanOrEqual(0)
    })

    it("should leave a side whose panel would be clamped back over its own task", () => {
        const narrow: PeekRect = {x: 0, y: 0, width: 420, height: 800}
        const anchor: PeekRect = {x: 100, y: 300, width: 220, height: 60}

        const placement = placePeek({anchor, panel: PANEL, canvas: narrow, isHorizontal: false, gap: GAP})

        expect(placement.side).toBe("below")
        expect(placement.y).toBe(anchor.y + anchor.height + GAP)
    })

    it("should take the side covering the least when every side is blocked", () => {
        const anchor = anchorAt(400, 300)
        const heavyRight: PeekRect = {x: anchor.x + anchor.width + GAP, y: 200, width: 400, height: 400}
        const lightLeft: PeekRect = {x: anchor.x - GAP - PANEL.width, y: 320, width: 20, height: 20}
        const heavyBelow: PeekRect = {x: 300, y: anchor.y + anchor.height + GAP, width: 400, height: 400}
        const heavyAbove: PeekRect = {x: 300, y: 0, width: 400, height: anchor.y}

        const placement = placePeek({
            anchor,
            panel: PANEL,
            canvas: CANVAS,
            isHorizontal: false,
            otherRects: [heavyRight, lightLeft, heavyBelow, heavyAbove],
            gap: GAP,
        })

        expect(placement.side).toBe("left")
    })
})
