import {afterEach, describe, expect, it, vi} from "vitest"
import {getRandomID} from "./id"

describe("getRandomID", () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    it("uses flow as the default prefix", () => {
        expect(getRandomID()).toMatch(/^flow_\d{7}$/)
    })

    it("uses a custom prefix as given", () => {
        expect(getRandomID("block")).toMatch(/^block_\d{7}$/)
    })

    it("always generates a 7-digit number at both ends of the range", () => {
        const random = vi.spyOn(Math, "random")

        random.mockReturnValue(0)
        expect(getRandomID()).toBe("flow_1000000")

        random.mockReturnValue(0.9999999)
        expect(getRandomID()).toBe("flow_9999999")
    })

    it("generates different ids on successive calls", () => {
        const first = getRandomID()
        const second = getRandomID()

        expect(second).not.toBe(first)
    })
})