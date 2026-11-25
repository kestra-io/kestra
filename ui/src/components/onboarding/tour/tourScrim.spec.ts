import {describe, expect, it} from "vitest"

import {scrimClipPath} from "./tourScrim"

const VIEWPORT = {width: 1000, height: 800}

const cutoutArcs = (path: string) => [...path.matchAll(/A(\S+),(\S+) 0 0 0/g)].map(([, rx, ry]) => [Number(rx), Number(ry)])

describe("scrimClipPath", () => {
    it("rounds every corner of the cutout with the given radius", () => {
        const path = scrimClipPath({top: 100, left: 200, bottom: 300, right: 500}, 10, VIEWPORT)

        expect(cutoutArcs(path)).toEqual([[10, 10], [10, 10], [10, 10], [10, 10]])
        expect(path).toContain("M210,100")
    })

    it("clamps the radius to half of the smallest side of the cutout", () => {
        const path = scrimClipPath({top: 100, left: 200, bottom: 108, right: 500}, 10, VIEWPORT)

        expect(cutoutArcs(path)).toEqual([[4, 4], [4, 4], [4, 4], [4, 4]])
    })
})
