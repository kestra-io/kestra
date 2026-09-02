import {describe, expect, it} from "vitest"
import {getSeparatorVariant} from "./sourceSearchDiff"

describe("getSeparatorVariant", () => {
    it("should replace hyphens with underscores", () => {
        expect(getSeparatorVariant("my-flow")).toBe("my_flow")
    })

    it("should replace underscores with hyphens", () => {
        expect(getSeparatorVariant("my_flow")).toBe("my-flow")
    })

    it("should return null when the query has no separator", () => {
        expect(getSeparatorVariant("myflow")).toBeNull()
    })
})