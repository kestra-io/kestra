import {describe, expect, it} from "vitest"
import {bestCorner, cornerOrder, cornerRect, coverScore, shiftRect} from "./tourPlacement"

// Offset from the viewport origin, as the menu and the top bar make it.
const AREA = {top: 60, left: 280, bottom: 860, right: 1480}
const CARD = {width: 400, height: 300}
const PAGE = AREA
const BOTTOM_RIGHT = {top: 660, left: 1180, bottom: 760, right: 1380}
const BOTTOM_LEFT = {top: 660, left: 380, bottom: 760, right: 580}

describe("tour card placement", () => {
    it("leaves the default corner when the target is under it", () => {
        expect(bestCorner(CARD, AREA, [BOTTOM_RIGHT], [])).toEqual({corner: "bottom-left", score: 0})
    })

    it("keeps the scene's corner when the target fills the page", () => {
        expect(bestCorner(CARD, AREA, [PAGE], [], cornerOrder("left")).corner).toBe("bottom-left")
    })

    it("stays off what a scene avoids inside a target that fills the page", () => {
        expect(bestCorner(CARD, AREA, [PAGE], [BOTTOM_LEFT], cornerOrder("left")).corner).toBe("bottom-right")
    })

    it("covers the target rather than what the scene avoids", () => {
        const bottom = {top: 660, left: 280, bottom: 860, right: 1480}
        const top = {top: 60, left: 280, bottom: 260, right: 1480}

        expect(bestCorner(CARD, AREA, [top], [bottom]).corner).toBe("top-right")
    })

    it("puts each corner inside the area", () => {
        expect(cornerRect("top-left", CARD, AREA)).toEqual({top: 60, left: 280, bottom: 360, right: 680})
        expect(cornerRect("bottom-right", CARD, AREA)).toEqual({top: 560, left: 1080, bottom: 860, right: 1480})
    })

    it("scores a dragged card where it was dragged to", () => {
        const corner = cornerRect("bottom-left", CARD, AREA)

        expect(coverScore(corner, [BOTTOM_RIGHT], [])).toBe(0)
        expect(coverScore(shiftRect(corner, {x: 800, y: 0}), [BOTTOM_RIGHT], [])).toBe(1)
    })
})
