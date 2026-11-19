import {describe, expect, it} from "vitest"
import {bestCorner, cornerOrder} from "./tourPlacement"

const AREA = {top: 0, left: 0, bottom: 800, right: 1200}
const CARD = {width: 400, height: 300}
const PAGE = AREA
const BOTTOM_RIGHT = {top: 600, left: 900, bottom: 700, right: 1100}
const BOTTOM_LEFT = {top: 600, left: 100, bottom: 700, right: 300}

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
        const bottom = {top: 600, left: 0, bottom: 800, right: 1200}
        const top = {top: 0, left: 0, bottom: 200, right: 1200}

        expect(bestCorner(CARD, AREA, [top], [bottom]).corner).toBe("top-right")
    })
})
